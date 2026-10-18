package com.bbh.itss.beadle.protech.config

import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.protech.application.calllog.CallerContext
import com.bbh.itss.beadle.protech.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry
import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.Lookup
import com.bbh.itss.beadle.protech.domain.change.LookupKind
import com.bbh.itss.beadle.protech.domain.change.ProTechMode
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.RaisedChange
import com.bbh.itss.beadle.protech.domain.change.TrackedChange
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.slf4j.MDC
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.GUI
import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.PROTECH
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.SUCCESS
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.L1
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.APPROVAL_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.watching
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class CallLogConfigurationSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    List<CallLogEntry> recorded = []
    boolean callLogDown = false
    int statusCalls = 0
    Map<String, ChangeActivity> moved = [:]
    List<String> logged = []

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration))
            .withUserConfiguration(CallLogConfiguration)
            .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)
            .withBean(CallLogPort, {
                [record: { CallLogEntry entry ->
                    if (callLogDown) {
                        throw new IllegalStateException('database down')
                    }
                    recorded << entry
                    entry
                }, find: { CallLogQuery query -> new CallLogPage(0, 0, 0, []) }] as CallLogPort
            } as Supplier<CallLogPort>)
            .withBean(ProTechPort, {
                [connected : { statusCalls++; false },
                 mode      : { statusCalls++; ProTechMode.DEMO },
                 raise     : { ProductionChange change -> Thread.sleep(2); new RaisedChange('CHG0031002', null) },
                 createTask: { String number, ChangeTask task -> 'CTASK0041009' },
                 read      : { Collection<ProductionChange> known -> [CHG0031001: raised()] },
                 update    : { ProductionChange change -> throw new IllegalStateException('ProTech does not change a closed change') },
                 remind    : { String number, ApprovalRef approval -> ['Olivia Bennett'] },
                 activity  : { Collection<TrackedChange> watched, Instant since ->
                     if (since == null) {
                         throw new UncheckedIOException('ProTech could not poll the changes: 503', new IOException())
                     }
                     moved
                 }] as ProTechPort
            } as Supplier<ProTechPort>)
            .withBean(ProTechLookupPort, {
                [connected: { true }, mode: { ProTechMode.LIVE },
                 find     : { LookupKind kind, String query, int limit -> [new Lookup('Technology Architecture', null)] }] as ProTechLookupPort
            } as Supplier<ProTechLookupPort>)
            .withBean(CallbackPort, {
                [notify: { ChangeCallback callback ->
                    logged << MDC.get('correlationId')
                    if (CallerContext.current().correlationId() == null) {
                        throw new UncheckedIOException('Beadle-GUI could not take the callback for CHG0031001: 503', new IOException())
                    }
                }] as CallbackPort
            } as Supplier<CallbackPort>)

    def cleanup() {
        CallerContext.clear()
    }

    def "every call of ProTech is written to the call log with the caller, its target, outcome and duration"() {
        given:
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))

        expect:
        runner.run { ApplicationContext context ->
            def proTech = context.getBean(ProTechPort)
            def lookups = context.getBean(ProTechLookupPort)
            assert proTech.raise(raised(number: null)) == new RaisedChange('CHG0031002', null)
            assert proTech.createTask('CHG0031001', releaseTask()) == 'CTASK0041009'
            assert proTech.read([raised(), raised(number: 'CHG0031002')]) == [CHG0031001: raised()]
            assert proTech.remind('CHG0031001', new ApprovalRef(L1, null)) == ['Olivia Bennett']
            assert proTech.remind('CHG0031001', new ApprovalRef(null, 'CTASK0041001')) == ['Olivia Bennett']
            assert lookups.find(ASSIGNMENT_GROUPS, 'Tech', 20) == [new Lookup('Technology Architecture', null)]
            assert !proTech.connected()
            assert proTech.mode() == ProTechMode.DEMO
            assert statusCalls == 2
            assert recorded*.operation() == ['raise', 'createTask', 'read', 'remind', 'remind', 'find']
            assert recorded*.target() == ['CHG0031002 CertScanner CERT 4.2: Expiry alerts',
                                          'CTASK0041009 CHG0031001 Task 3 of the CertScanner release',
                                          'CHG0031001,CHG0031002', 'CHG0031001 L1', 'CHG0031001 CTASK0041001',
                                          'assignment-groups Tech 20']
            with(recorded[0]) {
                assert id() == null
                assert at() == NOW
                assert userName() == 'Mateusz Matan'
                assert correlationId() == 'corr-1'
                assert system() == PROTECH
                assert outcome() == SUCCESS
                assert message() == null
                assert durationMs() >= 2
            }
            assert recorded.every { it.system() == PROTECH && it.outcome() == SUCCESS && it.correlationId() == 'corr-1' }
            true
        }
    }

    def "a failing call is written as a failure with its message and still thrown, without a caller when none is known"() {
        expect:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(ProTechPort).update(raised())
                assert false
            } catch (IllegalStateException e) {
                assert e.message == 'ProTech does not change a closed change'
            }
            assert recorded.size() == 1
            with(recorded[0]) {
                assert userName() == null
                assert correlationId() == null
                assert system() == PROTECH
                assert operation() == 'update'
                assert target() == 'CHG0031001'
                assert outcome() == FAILURE
                assert message() == 'ProTech does not change a closed change'
            }
            true
        }
    }

    def "a call log that cannot be written never fails the call"() {
        given:
        callLogDown = true

        expect:
        runner.run { ApplicationContext context ->
            assert context.getBean(ProTechPort).createTask('CHG0031001', task(1)) == 'CTASK0041009'
            assert recorded == []
            true
        }
    }

    def "the user and the target are cut to the columns of the call log"() {
        given:
        CallerContext.set(new Caller('u' * 300, 'corr-1'))

        expect:
        runner.run { ApplicationContext context ->
            context.getBean(ProTechPort).read((1..60).collect { raised(number: "CHG00310${it.toString().padLeft(2, '0')}") })
            with(recorded[0]) {
                assert userName().length() == CallLogEntry.USER_MAX
                assert correlationId() == 'corr-1'
                assert target().length() <= CallLogEntry.TARGET_MAX
                assert target().startsWith('CHG0031001,CHG0031002,')
                assert target().endsWith('...')
            }
            true
        }
    }

    def "the call log port itself is not intercepted"() {
        expect:
        runner.run { ApplicationContext context ->
            assert context.getBean(CallLogPort).find(CallLogQuery.of(null, null, null, 0, 10)).total() == 0
            assert recorded == []
            true
        }
    }

    def "a look at ProTech is written when it calls for a callback or failed, and left out when it found nothing new to Beadle-GUI"() {
        given: 'two changes Beadle-GUI knows in DRAFT, with every move until now'
        CallerContext.set(new Caller('system', 'poll-1'))
        def watched = [trackedChange(lastEventAt: NOW), trackedChange(number: 'CHG0031002', lastEventAt: NOW)]

        expect:
        runner.run { ApplicationContext context ->
            def proTech = context.getBean(ProTechPort)
            assert proTech.activity(watched, NOW) == [:]
            moved = [CHG0031001: new ChangeActivity('CHG0031001', DRAFT, NOW.minusSeconds(50), NOW.minusSeconds(20), NOW),
                     CHG0031003: new ChangeActivity('CHG0031003', BUSINESS_APPROVAL, NOW, null, null)]
            assert proTech.activity(watched, NOW.minusSeconds(60)) == moved
            assert recorded == []

            moved = [CHG0031001: new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW, null, null)]
            assert proTech.activity(watched, NOW.minusSeconds(60)) == moved
            moved = [CHG0031002: new ChangeActivity('CHG0031002', null, null, NOW.plusSeconds(1), null)]
            assert proTech.activity(watched, NOW.minusSeconds(60)) == moved
            try {
                proTech.activity(watching(['CHG0031001']), null)
                assert false
            } catch (UncheckedIOException e) {
                assert e.message == 'ProTech could not poll the changes: 503'
            }
            assert recorded*.operation() == ['activity'] * 3
            assert recorded*.target() == ['CHG0031001,CHG0031002 2026-10-15T08:59:00Z'] * 2 + ['CHG0031001']
            assert recorded*.outcome() == [SUCCESS, SUCCESS, FAILURE]
            assert recorded.every { it.system() == PROTECH && it.userName() == 'system' && it.correlationId() == 'poll-1' }
            true
        }
    }

    def "a callback to Beadle-GUI is written to the call log as a call of the system, also when it fails, and logged under its correlation id"() {
        expect:
        runner.run { ApplicationContext context ->
            def callbacks = context.getBean(CallbackPort)
            CallerContext.run(new Caller('system', 'corr-1')) {
                callbacks.notify(new ChangeCallback('CHG0031001', APPROVAL_CHANGED, DRAFT, NOW))
            }
            try {
                callbacks.notify(new ChangeCallback('CHG0031001', APPROVAL_CHANGED, DRAFT, NOW))
                assert false
            } catch (UncheckedIOException e) {
                assert e.message.startsWith('Beadle-GUI could not take the callback')
            }
            assert recorded*.system() == [GUI, GUI]
            assert recorded*.operation() == ['notify', 'notify']
            assert recorded*.target() == ['CHG0031001 APPROVAL_CHANGED', 'CHG0031001 APPROVAL_CHANGED']
            assert recorded*.outcome() == [SUCCESS, FAILURE]
            assert recorded*.userName() == ['system', null]
            assert recorded*.correlationId() == ['corr-1', null]
            assert logged == ['corr-1', null]
            assert MDC.get('correlationId') == null
            true
        }
    }
}

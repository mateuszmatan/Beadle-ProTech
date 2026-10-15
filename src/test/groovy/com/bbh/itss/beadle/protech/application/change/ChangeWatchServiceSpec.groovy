package com.bbh.itss.beadle.protech.application.change

import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.protech.application.calllog.CallerContext
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.domain.change.WatchReport
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.protech.application.change.ChangeWatchService.MARGIN
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.APPROVAL_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange

class ChangeWatchServiceSpec extends Specification {

    static final Instant NOW = CALLED.plusSeconds(600)
    static final Duration DEMO = Duration.ZERO
    static final Duration LIVE = Duration.ofMinutes(1)

    TrackedChangePort tracked = Mock()
    ProTechPort proTech = Mock()
    CallbackPort callbacks = Mock()
    MutableClock clock = new MutableClock(NOW)
    def service = new ChangeWatchService(tracked, proTech, callbacks, clock)

    def "ProTech is not asked while Beadle-GUI knows no open change"() {
        when:
        def report = service.tick()

        then:
        1 * tracked.watched() >> []
        0 * proTech.activity(*_)
        0 * callbacks._
        report == WatchReport.NOTHING
    }

    def "each change ProTech moved is told to Beadle-GUI once, as the system under the correlation id of the change"() {
        given:
        def moved = trackedChange(lastSeenAt: CALLED.plusSeconds(60))
        def approved = trackedChange(number: 'CHG0031002', state: BUSINESS_APPROVAL, correlationId: 'corr-2')
        def still = trackedChange(number: 'CHG0031003', lastSeenAt: CALLED.plusSeconds(30))
        def unknown = trackedChange(number: 'CHG0031004', state: null, lastSeenAt: null, lastEventAt: null)
        def movedTo = new ChangeActivity('CHG0031001', CTASK_APPROVAL, NOW.minusSeconds(5), null, null)
        def approvedAt = new ChangeActivity('CHG0031002', null, null, NOW.minusSeconds(3), null)
        def stillAt = new ChangeActivity('CHG0031003', DRAFT, CALLED, null, null)
        List<Caller> callers = []
        Caller pollCaller = null

        when:
        def report = service.tick()

        then:
        1 * tracked.watched() >> [moved, approved, still, unknown]
        1 * proTech.activity(['CHG0031001', 'CHG0031002', 'CHG0031003', 'CHG0031004'], CALLED.minus(MARGIN)) >> {
            pollCaller = CallerContext.current()
            [CHG0031001: movedTo, CHG0031002: approvedAt, CHG0031003: stillAt]
        }
        1 * callbacks.notify(new ChangeCallback('CHG0031001', STATE_CHANGED, CTASK_APPROVAL, NOW.minusSeconds(5), 'corr-1')) >> {
            callers << CallerContext.current()
        }
        1 * callbacks.notify(new ChangeCallback('CHG0031002', APPROVAL_CHANGED, BUSINESS_APPROVAL, NOW.minusSeconds(3), 'corr-2')) >> {
            callers << CallerContext.current()
        }
        1 * tracked.reported(moved, movedTo, NOW)
        1 * tracked.reported(approved, approvedAt, NOW)
        1 * tracked.seen(['CHG0031003', 'CHG0031004'], NOW)
        report.sent()*.number() == ['CHG0031001', 'CHG0031002']
        report.failures() == [:]
        callers == [new Caller('system', 'corr-1'), new Caller('system', 'corr-2')]
        pollCaller.user() == 'system'
        pollCaller.correlationId() ==~ /[0-9a-f-]{36}/
        CallerContext.current() == Caller.NONE
    }

    def "a callback Beadle-GUI does not take is reported, and the change is told again at the next look"() {
        given:
        def moved = trackedChange()
        def activity = new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW, null, null)
        proTech.pollEvery() >> DEMO
        tracked.watched() >> [moved]
        proTech.activity(*_) >> [CHG0031001: activity]

        when:
        def failed = service.tick()

        then:
        1 * callbacks.notify(_) >> { throw new UncheckedIOException('Beadle-GUI could not take the callback for CHG0031001: 503', new IOException()) }
        0 * tracked.reported(*_)
        0 * tracked.seen(*_)
        failed.sent() == []
        failed.failures() == [CHG0031001: 'Beadle-GUI could not take the callback for CHG0031001: 503']

        when:
        clock.instant = NOW.plusSeconds(10)
        def sent = service.tick()

        then:
        1 * callbacks.notify(new ChangeCallback('CHG0031001', STATE_CHANGED, BUSINESS_APPROVAL, NOW, 'corr-1'))
        1 * tracked.reported(moved, activity, NOW.plusSeconds(10))
        sent.sent()*.number() == ['CHG0031001']
    }

    def "a change whose report cannot be written down is reported as failed, and told again at the next look"() {
        given:
        def moved = trackedChange()
        def activity = new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW, null, null)
        proTech.pollEvery() >> DEMO
        tracked.watched() >> [moved]
        proTech.activity(*_) >> [CHG0031001: activity]

        when:
        def report = service.tick()

        then:
        1 * callbacks.notify(_)
        1 * tracked.reported(moved, activity, NOW) >> { throw new IllegalStateException('database down') }
        report.sent() == []
        report.failures() == [CHG0031001: 'database down']
    }

    def "the live ProTech is looked at once its poll interval has passed, also after a failed look"() {
        given:
        proTech.pollEvery() >> LIVE
        tracked.watched() >> [trackedChange()]

        when:
        service.tick()

        then:
        1 * proTech.activity(*_) >> { throw new UncheckedIOException('ProTech could not poll the changes: 503', new IOException()) }
        thrown(UncheckedIOException)

        when:
        clock.instant = NOW.plusSeconds(50)
        def early = service.tick()

        then:
        0 * tracked.watched()
        0 * proTech.activity(*_)
        early == WatchReport.NOTHING

        when:
        clock.instant = NOW.plusSeconds(60)
        service.tick()

        then:
        1 * proTech.activity(['CHG0031001'], CALLED.minus(MARGIN)) >> [:]
        1 * tracked.seen(['CHG0031001'], NOW.plusSeconds(60))
        0 * callbacks._
    }

    def "a look reaches back from the last move Beadle-GUI knows, or from now, when a change was never looked at"() {
        given:
        proTech.pollEvery() >> DEMO

        when:
        service.tick()

        then:
        1 * tracked.watched() >> [trackedChange(lastSeenAt: null, lastEventAt: CALLED.minusSeconds(60)),
                                  trackedChange(number: 'CHG0031002', lastSeenAt: NOW)]
        1 * proTech.activity(_, CALLED.minusSeconds(60).minus(MARGIN)) >> [:]

        when:
        clock.instant = NOW.plusSeconds(10)
        service.tick()

        then:
        1 * tracked.watched() >> [trackedChange(lastSeenAt: null, lastEventAt: null)]
        1 * proTech.activity(_, NOW.plusSeconds(10).minus(MARGIN)) >> [:]
    }
}

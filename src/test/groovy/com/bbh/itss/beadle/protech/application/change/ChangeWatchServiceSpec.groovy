package com.bbh.itss.beadle.protech.application.change

import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.protech.application.calllog.CallerContext
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Instant

import static com.bbh.itss.beadle.protech.application.change.ChangeWatchService.MARGIN
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.APPROVAL_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange

class ChangeWatchServiceSpec extends Specification {

    static final Instant NOW = CALLED.plusSeconds(600)

    TrackedChangePort tracked = Mock()
    ProTechPort proTech = Mock()
    CallbackPort callbacks = Mock()
    MutableClock clock = new MutableClock(NOW)
    def service = new ChangeWatchService(tracked, proTech, callbacks, clock)

    def "ProTech is not asked while Beadle-GUI knows no open change"() {
        when:
        service.tick()

        then:
        1 * tracked.watched() >> []
        0 * proTech.activity(*_)
        0 * callbacks._
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
        service.tick()

        then:
        1 * tracked.watched() >> [moved, approved, still, unknown]
        1 * proTech.activity(['CHG0031001', 'CHG0031002', 'CHG0031003', 'CHG0031004'], CALLED.minus(MARGIN)) >> {
            pollCaller = CallerContext.current()
            [CHG0031001: movedTo, CHG0031002: approvedAt, CHG0031003: stillAt]
        }
        1 * callbacks.notify(new ChangeCallback('CHG0031001', STATE_CHANGED, CTASK_APPROVAL, NOW.minusSeconds(5))) >> {
            callers << CallerContext.current()
        }
        1 * callbacks.notify(new ChangeCallback('CHG0031002', APPROVAL_CHANGED, BUSINESS_APPROVAL, NOW.minusSeconds(3))) >> {
            callers << CallerContext.current()
        }
        1 * tracked.reported(moved, movedTo, NOW)
        1 * tracked.reported(approved, approvedAt, NOW)
        1 * tracked.seen(['CHG0031003', 'CHG0031004'], NOW)
        callers == [new Caller('system', 'corr-1'), new Caller('system', 'corr-2')]
        pollCaller.user() == 'system'
        pollCaller.correlationId() ==~ /[0-9a-f-]{36}/
        CallerContext.current() == Caller.NONE
    }

    def "a callback Beadle-GUI does not take leaves the change unreported, so it is told again at the next look"() {
        given:
        def moved = trackedChange()
        def activity = new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW, null, null)
        tracked.watched() >> [moved]
        proTech.activity(*_) >> [CHG0031001: activity]

        when:
        service.tick()

        then:
        1 * callbacks.notify(_) >> { throw new UncheckedIOException('Beadle-GUI could not take the callback for CHG0031001: 503', new IOException()) }
        0 * tracked.reported(*_)
        0 * tracked.seen(*_)

        when:
        clock.instant = NOW.plusSeconds(10)
        service.tick()

        then:
        1 * callbacks.notify(new ChangeCallback('CHG0031001', STATE_CHANGED, BUSINESS_APPROVAL, NOW))
        1 * tracked.reported(moved, activity, NOW.plusSeconds(10))
    }

    def "a report that cannot be written down fails the look, and the change is told again at the next one"() {
        given:
        def moved = trackedChange()
        def activity = new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW, null, null)
        tracked.watched() >> [moved]
        proTech.activity(*_) >> [CHG0031001: activity]

        when:
        service.tick()

        then:
        1 * callbacks.notify(_)
        1 * tracked.reported(moved, activity, NOW) >> { throw new IllegalStateException('database down') }
        thrown(IllegalStateException)
    }

    def "a webhook look asks ProTech about one followed change at once"() {
        given:
        def change = trackedChange(number: 'CHG0031002', lastSeenAt: CALLED.plusSeconds(30), correlationId: 'corr-2')
        def activity = new ChangeActivity('CHG0031002', null, null, NOW.minusSeconds(3), null)

        when:
        service.look('CHG0031002')

        then:
        1 * tracked.find('CHG0031002') >> Optional.of(change)
        1 * proTech.activity(['CHG0031002'], CALLED.plusSeconds(30).minus(MARGIN)) >> [CHG0031002: activity]
        1 * callbacks.notify(new ChangeCallback('CHG0031002', APPROVAL_CHANGED, DRAFT, NOW.minusSeconds(3)))
        1 * tracked.reported(change, activity, NOW)
    }

    def "a webhook look at a change not followed, or no longer, asks ProTech nothing"() {
        when:
        service.look(number)

        then:
        1 * tracked.find(number) >> found
        0 * proTech.activity(*_)

        where:
        number       || found
        'CHG0039999' || Optional.empty()
        'CHG0031001' || Optional.of(trackedChange(state: CLOSED))
    }

    def "a webhook look needs the number of a change"() {
        when:
        service.look(' ')

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('number', 'is required')]
        0 * tracked._
    }

    def "a look reaches back from the last move Beadle-GUI knows, or from now, when a change was never looked at"() {
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

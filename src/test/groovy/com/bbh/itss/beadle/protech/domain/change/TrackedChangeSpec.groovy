package com.bbh.itss.beadle.protech.domain.change

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.APPROVAL_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.TASK_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange

class TrackedChangeSpec extends Specification {

    static final Instant LATER = CALLED.plusSeconds(600)

    def "a change seen by Beadle-GUI is known to it up to that moment"() {
        expect:
        TrackedChange.seen('CHG0031001', DRAFT, CALLED, 'corr-1') == trackedChange()
    }

    def "a change is watched until it is closed, also while its state is unknown"() {
        expect:
        trackedChange(state: state).watched() == watched

        where:
        state            || watched
        DRAFT            || true
        PRIMARY_APPROVAL || true
        IMPLEMENTATION   || true
        null             || true
        CLOSED           || false
    }

    def "a change seen again takes the newer state and correlation id and keeps the latest times"() {
        expect:
        trackedChange().updatedBy(trackedChange(state: PRIMARY_APPROVAL, lastSeenAt: LATER, lastEventAt: LATER,
                correlationId: 'corr-2')) ==
                trackedChange(state: PRIMARY_APPROVAL, lastSeenAt: LATER, lastEventAt: LATER, correlationId: 'corr-2')
        trackedChange(lastSeenAt: LATER, lastEventAt: LATER).updatedBy(trackedChange(state: IMPLEMENTATION)) ==
                trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER, lastEventAt: LATER)
        trackedChange(state: IMPLEMENTATION).updatedBy(trackedChange(state: null, correlationId: null)) ==
                trackedChange(state: IMPLEMENTATION)
        trackedChange(lastSeenAt: null, lastEventAt: null).updatedBy(trackedChange()) == trackedChange()
    }

    def "a change Beadle-GUI acted on is known to it up to then, in the state it was"() {
        expect:
        TrackedChange.touched('CHG0031001', LATER, 'corr-2') ==
                new TrackedChange('CHG0031001', null, LATER, LATER, 'corr-2')
        trackedChange(state: CTASK_APPROVAL).updatedBy(TrackedChange.touched('CHG0031001', LATER, 'corr-2')) ==
                trackedChange(state: CTASK_APPROVAL, lastSeenAt: LATER, lastEventAt: LATER, correlationId: 'corr-2')
    }

    def "Beadle-GUI is told #event when ProTech #moved"() {
        given:
        def known = trackedChange(state: PRIMARY_APPROVAL, lastEventAt: CALLED)

        expect:
        known.callbackFor(new ChangeActivity('CHG0031001', state, stateAt, approvalsAt, tasksAt), LATER) ==
                Optional.ofNullable(event == null ? null
                        : new ChangeCallback('CHG0031001', event, current, at, 'corr-1'))

        where:
        moved                                      | state            | stateAt        | approvalsAt             | tasksAt                 || event            | current          | at
        'moved the change to a new state'          | IMPLEMENTATION   | LATER          | LATER                   | LATER                   || STATE_CHANGED    | IMPLEMENTATION   | LATER
        'moved it without telling when'            | IMPLEMENTATION   | null           | null                    | null                    || STATE_CHANGED    | IMPLEMENTATION   | LATER
        'approved after the last known move'       | PRIMARY_APPROVAL | CALLED         | LATER                   | LATER                   || APPROVAL_CHANGED | PRIMARY_APPROVAL | LATER
        'approved, saying nothing of the state'    | null             | null           | LATER                   | null                    || APPROVAL_CHANGED | PRIMARY_APPROVAL | LATER
        'moved a task after the last known move'   | PRIMARY_APPROVAL | CALLED         | CALLED                  | LATER                   || TASK_CHANGED     | PRIMARY_APPROVAL | LATER
        'moved nothing new'                        | PRIMARY_APPROVAL | CALLED         | CALLED                  | CALLED.minusSeconds(60) || null             | null             | null
        'told nothing at all'                      | null             | null           | null                    | null                    || null             | null             | null
    }

    def "a change Beadle-GUI knows no move of hears of any"() {
        expect:
        trackedChange(lastEventAt: null).callbackFor(new ChangeActivity('CHG0031001', DRAFT, CALLED, null, CALLED),
                LATER) == Optional.of(new ChangeCallback('CHG0031001', TASK_CHANGED, DRAFT, CALLED, 'corr-1'))
    }

    def "a reported change knows the new state and every move until the latest one"() {
        expect:
        trackedChange().reported(new ChangeActivity('CHG0031001', IMPLEMENTATION, CALLED.plusSeconds(60),
                LATER, CALLED.plusSeconds(120)), LATER.plusSeconds(5)) ==
                trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER.plusSeconds(5), lastEventAt: LATER)
        trackedChange().reported(new ChangeActivity('CHG0031001', null, null, null, LATER), LATER) ==
                trackedChange(lastSeenAt: LATER, lastEventAt: LATER)
    }

    def "the latest move of an activity is the last of its times"() {
        expect:
        new ChangeActivity('CHG0031001', DRAFT, CALLED, LATER, null).latest() == LATER
        new ChangeActivity('CHG0031001', null, null, null, CALLED).latest() == CALLED
        new ChangeActivity('CHG0031001', DRAFT, null, null, null).latest() == null
    }
}

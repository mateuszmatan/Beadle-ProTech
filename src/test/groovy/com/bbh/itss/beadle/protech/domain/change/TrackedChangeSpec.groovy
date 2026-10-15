package com.bbh.itss.beadle.protech.domain.change

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
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
}

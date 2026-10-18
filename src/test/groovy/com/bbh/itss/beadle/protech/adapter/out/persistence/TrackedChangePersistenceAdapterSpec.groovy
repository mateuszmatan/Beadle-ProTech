package com.bbh.itss.beadle.protech.adapter.out.persistence

import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange

class TrackedChangePersistenceAdapterSpec extends PersistenceSpecification {

    static final Instant LATER = CALLED.plusSeconds(600)

    @Subject
    @Autowired
    TrackedChangePersistenceAdapter adapter

    def setup() {
        jdbc.update('DELETE FROM BEADLE_PROTECH_CHANGE')
    }

    def "a change created or read is written down, and seen again it is merged over what was known"() {
        when:
        adapter.track([trackedChange(), trackedChange(number: 'CHG0031002', state: null)])

        then:
        adapter.find('CHG0031001') == Optional.of(trackedChange())
        adapter.find('CHG0031002') == Optional.of(trackedChange(number: 'CHG0031002', state: null))
        adapter.find('CHG0031003') == Optional.empty()
        jdbc.queryForMap("SELECT CHANGE_NUMBER, STATE, CORRELATION_ID FROM BEADLE_PROTECH_CHANGE WHERE CHANGE_NUMBER = 'CHG0031001'") ==
                [CHANGE_NUMBER: 'CHG0031001', STATE: 'DRAFT', CORRELATION_ID: 'corr-1']

        when:
        adapter.track([trackedChange(state: PRIMARY_APPROVAL, lastSeenAt: LATER, lastEventAt: LATER, correlationId: 'corr-2')])
        adapter.track([trackedChange(state: IMPLEMENTATION, correlationId: 'corr-3')])

        then:
        adapter.find('CHG0031001') == Optional.of(trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER,
                lastEventAt: LATER, correlationId: 'corr-3'))
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_PROTECH_CHANGE', Integer) == 2
    }

    def "a change that cannot be written down is reported and the others are still followed"() {
        when:
        adapter.track([trackedChange(number: 'CHG' + '9' * 40), trackedChange()])

        then:
        noExceptionThrown()
        adapter.find('CHG0031001') == Optional.of(trackedChange())
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_PROTECH_CHANGE', Integer) == 1
    }

    def "a change Beadle-GUI acted on is known to it up to then, and a change not followed stays so"() {
        given:
        adapter.track([trackedChange(state: IMPLEMENTATION)])

        when:
        adapter.touch('CHG0031001', LATER, 'corr-2')
        adapter.touch('CHG0031002', LATER, 'corr-2')

        then:
        adapter.find('CHG0031001') == Optional.of(trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER,
                lastEventAt: LATER, correlationId: 'corr-2'))
        adapter.find('CHG0031002') == Optional.empty()

        when:
        adapter.touch('CHG' + '9' * 40, LATER, 'corr-3')

        then:
        noExceptionThrown()
    }

    def "the open changes are watched by number, a reported change takes the activity and a look notes when it was"() {
        given:
        adapter.track([trackedChange(number: 'CHG0031003'), trackedChange(number: 'CHG0031001', state: null),
                       trackedChange(number: 'CHG0031002', state: CLOSED)])

        expect:
        adapter.watched()*.number() == ['CHG0031001', 'CHG0031003']

        when:
        adapter.reported(trackedChange(number: 'CHG0031003'), new ChangeActivity('CHG0031003', CLOSED, LATER, null,
                null), LATER)
        adapter.seen(['CHG0031001', 'CHG0031009'], LATER)

        then:
        adapter.watched() == [trackedChange(number: 'CHG0031001', state: null, lastSeenAt: LATER)]
        adapter.find('CHG0031003') == Optional.of(trackedChange(number: 'CHG0031003', state: CLOSED,
                lastSeenAt: LATER, lastEventAt: LATER))
    }

    def "a report keeps the read and the action Beadle-GUI noted on the change since the look"() {
        given: 'the watch looks at the change and finds it moved'
        adapter.track([trackedChange()])
        def looked = adapter.watched().first()
        def activity = new ChangeActivity('CHG0031001', PRIMARY_APPROVAL, CALLED.plusSeconds(60), null, null)

        and: 'Beadle-GUI reads the change while it takes the callback, and then reminds an approver'
        adapter.track([trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER, lastEventAt: LATER, correlationId: 'corr-read')])
        adapter.touch('CHG0031001', LATER.plusSeconds(5), 'corr-remind')

        when:
        adapter.reported(looked, activity, CALLED.plusSeconds(90))

        then:
        adapter.find('CHG0031001') == Optional.of(trackedChange(state: IMPLEMENTATION, lastSeenAt: LATER.plusSeconds(5),
                lastEventAt: LATER.plusSeconds(5), correlationId: 'corr-remind'))
    }

    def "a look never moves back the time a change was last seen"() {
        given:
        adapter.track([trackedChange(lastSeenAt: LATER)])

        when:
        adapter.seen(['CHG0031001'], CALLED.plusSeconds(60))

        then:
        adapter.find('CHG0031001').get().lastSeenAt() == LATER
    }

    def "a look at more changes than Oracle takes in one list notes them all"() {
        given:
        def numbers = (1..1001).collect { 'CHG' + (1000000 + it) }
        adapter.track(numbers.collect { trackedChange(number: it) })

        when:
        adapter.seen(numbers, LATER)

        then:
        adapter.watched()*.lastSeenAt().every { it == LATER }
        adapter.watched().size() == 1001
    }

    def "a report that cannot be written down fails"() {
        when:
        adapter.reported(trackedChange(number: 'CHG' + '9' * 40), new ChangeActivity('CHG' + '9' * 40, CLOSED, LATER,
                null, null), LATER)

        then:
        thrown(RuntimeException)
    }
}

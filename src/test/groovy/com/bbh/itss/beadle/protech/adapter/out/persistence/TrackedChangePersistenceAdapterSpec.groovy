package com.bbh.itss.beadle.protech.adapter.out.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:tracked-change-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(TrackedChangePersistenceAdapter)
class TrackedChangePersistenceAdapterSpec extends Specification {

    static final Instant LATER = CALLED.plusSeconds(600)

    @Subject
    @Autowired
    TrackedChangePersistenceAdapter adapter

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    PlatformTransactionManager transactionManager

    def setup() {
        jdbc.update('DELETE FROM BEADLE_PROTECH_CHANGE')
    }

    def "a change raised or read is written down, and seen again it is merged over what was known"() {
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

    def "a change is followed even when the transaction around it rolls back"() {
        when:
        new TransactionTemplate(transactionManager).execute { status ->
            adapter.track([trackedChange()])
            status.setRollbackOnly()
        }

        then:
        adapter.find('CHG0031001') == Optional.of(trackedChange())
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

    def "the open changes are watched by number, a saved change is written as it is and a look notes when it was"() {
        given:
        adapter.track([trackedChange(number: 'CHG0031003'), trackedChange(number: 'CHG0031001', state: null),
                       trackedChange(number: 'CHG0031002', state: CLOSED)])

        expect:
        adapter.watched()*.number() == ['CHG0031001', 'CHG0031003']

        when:
        adapter.save(trackedChange(number: 'CHG0031003', state: CLOSED, lastEventAt: LATER, correlationId: 'corr-9'))
        adapter.seen(['CHG0031001', 'CHG0031009'], LATER)

        then:
        adapter.watched() == [trackedChange(number: 'CHG0031001', state: null, lastSeenAt: LATER)]
        adapter.find('CHG0031003') == Optional.of(trackedChange(number: 'CHG0031003', state: CLOSED,
                lastEventAt: LATER, correlationId: 'corr-9'))
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

    def "a change that cannot be saved fails the save"() {
        when:
        adapter.save(trackedChange(number: 'CHG' + '9' * 40))

        then:
        thrown(RuntimeException)
    }
}

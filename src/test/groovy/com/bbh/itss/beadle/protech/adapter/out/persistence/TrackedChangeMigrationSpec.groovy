package com.bbh.itss.beadle.protech.adapter.out.persistence

import com.bbh.itss.beadle.protech.domain.change.ChangeState
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import java.sql.Timestamp

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:file:./build/tracked-change-migration/db;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class TrackedChangeMigrationSpec extends MigrationSpecification {

    static final String TABLE = 'BEADLE_PROTECH_CHANGE'
    static final List<String> COLUMNS = ['CHANGE_NUMBER', 'STATE', 'LAST_SEEN_AT', 'LAST_EVENT_AT', 'CORRELATION_ID']
    static final String INSERT = "INSERT INTO $TABLE (CHANGE_NUMBER, STATE, LAST_SEEN_AT, LAST_EVENT_AT, CORRELATION_ID) VALUES (?, ?, ?, ?, 'corr-1')"
    static final Timestamp RAISED = new Timestamp(0)

    def setup() {
        jdbc.execute('DROP ALL OBJECTS')
    }

    def "002 creates the followed changes on top of the call log, and rolls back to the call log alone"() {
        when:
        liquibase.update('')

        then:
        ['BEADLE_PROTECH_CALL_LOG', TABLE].every { it in tables() }
        columns(TABLE) == COLUMNS
        ['STATE', 'LAST_SEEN_AT', 'LAST_EVENT_AT', 'CORRELATION_ID'].every { nullable(TABLE, it) }
        !nullable(TABLE, 'CHANGE_NUMBER')
        'IX_BEADLE_PROTECH_CHANGE_STATE' in indexes(TABLE)

        when:
        ChangeState.values().eachWithIndex { state, index -> jdbc.update(INSERT, "CHG000000$index", state.name(), RAISED, RAISED) }
        jdbc.update(INSERT, 'CHG0031001', null, null, null)

        then:
        jdbc.queryForList("SELECT STATE FROM $TABLE ORDER BY CHANGE_NUMBER", String) ==
                ChangeState.values()*.name() + [null]

        when:
        rollBackSince('002-')

        then:
        !(TABLE in tables())
        'BEADLE_PROTECH_CALL_LOG' in tables()

        when:
        liquibase.update('')

        then:
        TABLE in tables()
        jdbc.queryForObject("SELECT COUNT(*) FROM $TABLE", Integer) == 0
    }

    def "a change is followed once, always by its number and in a state of the contract"() {
        given:
        liquibase.update('')
        jdbc.update(INSERT, 'CHG0031001', 'DRAFT', RAISED, RAISED)

        when:
        jdbc.update(INSERT, number, state, RAISED, RAISED)

        then:
        thrown(DataIntegrityViolationException)

        where:
        number       | state
        'CHG0031001' | 'DRAFT'
        null         | 'DRAFT'
        'CHG0031002' | 'Assess'
        'CHG0031002' | 'implementation'
        'C' * 41     | 'DRAFT'
    }

    private List<String> indexes(String table) {
        jdbc.queryForList('SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ?', String, table)
    }
}

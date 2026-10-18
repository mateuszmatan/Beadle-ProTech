package com.bbh.itss.beadle.protech.adapter.out.persistence

import com.bbh.itss.beadle.protech.domain.change.ChangeState
import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.FastCheckService
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Timestamp

/** The Liquibase changesets on H2 in Oracle mode, without a Spring context: what each creates and rolls back. */
class MigrationsSpec extends Specification {

    static final String CALL_LOG = 'BEADLE_PROTECH_CALL_LOG'
    static final String CHANGE = 'BEADLE_PROTECH_CHANGE'
    static final String LOG_ROW = """INSERT INTO $CALL_LOG (AT, USER_NAME, CORRELATION_ID, SYSTEM, OPERATION, TARGET,
            OUTCOME, MESSAGE, DURATION_MS) VALUES (CURRENT_TIMESTAMP, 'Mateusz Matan', 'corr-1', ?, 'raise', 'CHG0031001', ?, NULL, 12)"""
    static final String CHANGE_ROW = "INSERT INTO $CHANGE (CHANGE_NUMBER, STATE, LAST_SEEN_AT, LAST_EVENT_AT, CORRELATION_ID) VALUES (?, ?, ?, ?, 'corr-1')"
    static final Timestamp RAISED = new Timestamp(0)

    @Shared
    @AutoCleanup('destroy')
    SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:migrations-${UUID.randomUUID()};MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", 'sa', '', true)
    @Shared
    JdbcTemplate jdbc = new JdbcTemplate(dataSource)
    @Shared
    @AutoCleanup
    Liquibase liquibase

    def setupSpec() {
        System.setProperty('liquibase.analytics.enabled', 'false')
        liquibase = new Liquibase('db/changelog/db.changelog-master.yaml', new ClassLoaderResourceAccessor(),
                DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(dataSource.connection)))
    }

    def setup() {
        jdbc.execute('DROP ALL OBJECTS')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()
        liquibase.update('')
    }

    def "the changesets create the call log and the followed changes, and roll back one by one"() {
        expect:
        columns(CALL_LOG) == ['ID', 'AT', 'USER_NAME', 'CORRELATION_ID', 'SYSTEM', 'OPERATION', 'TARGET', 'OUTCOME',
                              'MESSAGE', 'DURATION_MS']
        nullable(CALL_LOG) == ['ID': false, 'AT': false, 'USER_NAME': true, 'CORRELATION_ID': true, 'SYSTEM': false,
                               'OPERATION': false, 'TARGET': true, 'OUTCOME': false, 'MESSAGE': true, 'DURATION_MS': false]
        indexes(CALL_LOG).containsAll(['IX_BEADLE_PROTECH_CALL_LOG_AT', 'IX_BEADLE_PROTECH_CALL_LOG_CORRELATION'])
        columns(CHANGE) == ['CHANGE_NUMBER', 'STATE', 'LAST_SEEN_AT', 'LAST_EVENT_AT', 'CORRELATION_ID']
        nullable(CHANGE).findAll { it.value }.keySet() == ['STATE', 'LAST_SEEN_AT', 'LAST_EVENT_AT', 'CORRELATION_ID'] as Set
        'IX_BEADLE_PROTECH_CHANGE_STATE' in indexes(CHANGE)

        when:
        jdbc.update(LOG_ROW, 'PROTECH', 'SUCCESS')
        jdbc.update(LOG_ROW, 'GUI', 'FAILURE')
        ChangeState.values().eachWithIndex { state, index -> jdbc.update(CHANGE_ROW, "CHG000000$index", state.name(), RAISED, RAISED) }
        jdbc.update(CHANGE_ROW, 'CHG0031001', null, null, null)

        then:
        jdbc.queryForList("SELECT ID FROM $CALL_LOG ORDER BY ID", Long) == [1L, 2L]
        jdbc.queryForList("SELECT STATE FROM $CHANGE ORDER BY CHANGE_NUMBER", String) == ChangeState.values()*.name() + [null]

        when:
        def rolledBack = (1..2).collect { tablesAfterRollingBack(1) }
        liquibase.update('')

        then:
        rolledBack == [[CALL_LOG] as Set, [] as Set]
        tables() == [CALL_LOG, CHANGE] as Set
        jdbc.queryForObject("SELECT COUNT(*) FROM $CALL_LOG", Integer) == 0
    }

    def "a row with #problem is refused by its table"() {
        given:
        jdbc.update(CHANGE_ROW, 'CHG0031001', 'DRAFT', RAISED, RAISED)

        when:
        jdbc.update(sql, values as Object[])

        then:
        thrown(DataIntegrityViolationException)

        where:
        problem                        | sql        | values
        'an unknown system'            | LOG_ROW    | ['JIRA', 'SUCCESS']
        'an unknown outcome'           | LOG_ROW    | ['PROTECH', 'PARTIAL']
        'no system'                    | LOG_ROW    | [null, 'SUCCESS']
        'no outcome'                   | LOG_ROW    | ['GUI', null]
        'a change followed twice'      | CHANGE_ROW | ['CHG0031001', 'DRAFT', RAISED, RAISED]
        'a change without a number'    | CHANGE_ROW | [null, 'DRAFT', RAISED, RAISED]
        'a state of ProTech'           | CHANGE_ROW | ['CHG0031002', 'Assess', RAISED, RAISED]
        'a state in lower case'        | CHANGE_ROW | ['CHG0031002', 'implementation', RAISED, RAISED]
        'a number longer than 40'      | CHANGE_ROW | ['C' * 41, 'DRAFT', RAISED, RAISED]
    }

    private Set<String> tablesAfterRollingBack(int changesets) {
        liquibase.rollback(changesets, '')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()
        tables()
    }

    private Set<String> tables() {
        jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'BEADLE%'", String) as Set
    }

    private List<String> columns(String table) {
        jdbc.queryForList('SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION',
                String, table)
    }

    private Map<String, Boolean> nullable(String table) {
        jdbc.queryForList('SELECT COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION',
                table).collectEntries { [it.COLUMN_NAME, it.IS_NULLABLE == 'YES'] }
    }

    private List<String> indexes(String table) {
        jdbc.queryForList('SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ?', String, table)
    }
}

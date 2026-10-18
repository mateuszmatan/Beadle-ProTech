package com.bbh.itss.beadle.protech.adapter.out.persistence

import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Subject

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.GUI
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.callLogEntry

class CallLogPersistenceAdapterSpec extends PersistenceSpecification {

    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')

    @Subject
    @Autowired
    CallLogPersistenceAdapter adapter

    def setup() {
        jdbc.update('DELETE FROM BEADLE_PROTECH_CALL_LOG')
    }

    def "an entry is written with a generated id and read back as it was"() {
        when:
        def recorded = adapter.record(callLogEntry(id: null))
        def failed = adapter.record(callLogEntry(id: null, system: GUI, operation: 'notify',
                target: 'CHG0031001 STATE_CHANGED', outcome: FAILURE,
                message: 'Beadle-GUI did not take the callback of CHG0031001: 503', durationMs: 345L))

        then:
        recorded == callLogEntry(id: recorded.id())
        recorded.id() != null
        failed.id() > recorded.id()
        adapter.find(CallLogQuery.of(null, null, null, 0, 10)).entries() == [failed, recorded]
        jdbc.queryForMap('SELECT SYSTEM, OPERATION, OUTCOME, MESSAGE, DURATION_MS FROM BEADLE_PROTECH_CALL_LOG WHERE ID = ?', failed.id()) ==
                [SYSTEM : 'GUI', OPERATION: 'notify', OUTCOME: 'FAILURE',
                 MESSAGE: 'Beadle-GUI did not take the callback of CHG0031001: 503', DURATION_MS: 345]
    }

    def "the entries are found by correlation id and time window, newest first, page by page"() {
        given:
        def entries = (0..<5).collect { index ->
            adapter.record(callLogEntry(id: null, at: NOON.plusSeconds(index * 60),
                    correlationId: index % 2 == 0 ? 'even' : 'odd', system: index == 4 ? GUI : callLogEntry().system(),
                    operation: "call-$index"))
        }

        expect:
        adapter.find(CallLogQuery.of(correlationId, from, to, page, size)) ==
                new CallLogPage(page, size, total, expected.collect { entries[it] })

        where:
        correlationId | from                 | to                    | page | size || total | expected
        null          | null                 | null                  | 0    | 10   || 5     | [4, 3, 2, 1, 0]
        null          | null                 | null                  | 1    | 2    || 5     | [2, 1]
        null          | null                 | null                  | 3    | 2    || 5     | []
        'even'        | null                 | null                  | 0    | 10   || 3     | [4, 2, 0]
        ' odd '       | null                 | null                  | 0    | 10   || 2     | [3, 1]
        null          | NOON.plusSeconds(60) | NOON.plusSeconds(180) | 0    | 10   || 2     | [2, 1]
        'even'        | NOON.plusSeconds(60) | null                  | 0    | 10   || 2     | [4, 2]
        null          | null                 | NOON                  | 0    | 10   || 0     | []
    }
}

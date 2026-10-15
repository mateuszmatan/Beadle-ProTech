package com.bbh.itss.beadle.protech.regression

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification

import java.time.Instant

import static java.time.Duration.ofHours
import static java.time.Duration.ofMinutes

class CallLogRegressionSpec extends BeadleProTechSpecification {

    def "every call to ProTech fills the call log, newest first, with who caused it and how it went"() {
        given:
        Instant start = clock.instant()
        api.get('/api/protech/lookups/users?q=Olivia')
        moveTo(start + ofMinutes(1))
        api.get('/api/protech/lookups/releases?q=CERT&limit=5')
        moveTo(start + ofMinutes(2))
        api.post('/api/protech/changes/CHG0000002/reminders', [role: 'SUPPORT'])

        when:
        def page = api.get("/api/protech/call-log?correlationId=$correlationId")

        then:
        page.status == 200
        page.json.subMap('page', 'size', 'total') == [page: 0, size: 50, total: 3]
        page.json.entries*.subMap('at', 'userName', 'correlationId', 'system', 'operation', 'target', 'outcome', 'message') == [
                [at     : (start + ofMinutes(2)).toString(), userName: USER, correlationId: correlationId,
                 system : 'PROTECH', operation: 'remind', target: 'CHG0000002 SUPPORT', outcome: 'FAILURE',
                 message: 'ProTech has no change CHG0000002'],
                [at     : (start + ofMinutes(1)).toString(), userName: USER, correlationId: correlationId,
                 system : 'PROTECH', operation: 'find', target: 'releases CERT 5', outcome: 'SUCCESS', message: null],
                [at     : start.toString(), userName: USER, correlationId: correlationId, system: 'PROTECH',
                 operation: 'find', target: 'users Olivia 20', outcome: 'SUCCESS', message: null]]
        page.json.entries.every { it.id instanceof Number && it.durationMs >= 0 }

        when:
        def second = api.get("/api/protech/call-log?correlationId=$correlationId&page=1&size=1")
        def window = api.get("/api/protech/call-log?correlationId=$correlationId&from=${start + ofMinutes(1)}&to=${start + ofMinutes(2)}")

        then:
        second.json.subMap('page', 'size', 'total') == [page: 1, size: 1, total: 3]
        second.json.entries*.target == ['releases CERT 5']
        window.json.entries*.target == ['releases CERT 5']
    }

    def "a call without a correlation id is logged under a fresh one, and one without a user without a name"() {
        given:
        def tokenOnly = anonymous.withHeaders([Authorization: "Bearer $TOKEN" as String])
        moveTo(clock.instant() + ofHours(1))
        Instant start = clock.instant()

        when:
        tokenOnly.withHeaders(['X-Beadle-User': USER]).get('/api/protech/lookups/problems?q=pool')
        tokenOnly.get('/api/protech/lookups/clients?q=Kestrel')
        def logged = api.get("/api/protech/call-log?from=$start&size=500").json.entries
                .findAll { it.target in ['problems pool 20', 'clients Kestrel 20'] }

        then:
        logged*.target == ['clients Kestrel 20', 'problems pool 20']
        logged*.userName == [null, USER]
        logged.every { it.correlationId ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ }
        logged*.correlationId.unique().size() == 2
    }

    def "a call log query with #problem is refused naming the field"() {
        when:
        def response = api.get('/api/protech/call-log?' + query)

        then:
        response.status == 400
        response.header('Content-Type').startsWith('application/problem+json')
        response.json.problems*.field == [field]

        where:
        problem              | query                                               || field
        'a negative page'    | 'page=-1'                                           || 'page'
        'an empty page'      | 'size=0'                                            || 'size'
        'a page too large'   | 'size=501'                                          || 'size'
        'a window backwards' | 'from=2026-10-15T10:00:00Z&to=2026-10-15T09:00:00Z' || 'from'
    }
}

package com.bbh.itss.beadle.protech.performance

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification
import com.bbh.itss.beadle.protech.support.Json

import static java.time.Duration.ofDays

class ProTechPerformanceSpec extends BeadleProTechSpecification {

    def "creating a change, reading changes back and looking up users answer within 500 ms at the 95th percentile"() {
        given: 'ten changes ProTech holds'
        def start = clock.instant()
        def known = (1..10).collect { draft(start, start + ofDays(3)).toBuilder().number(create(draft(start, start + ofDays(3)))).build() }
        String read = Json.jackson(known)

        when:
        def creates = LatencyStats.measure(20, 4) {
            api.postRaw('/api/protech/changes', 'application/json', Json.jackson(draft(start, start + ofDays(3)))).status == 201
        }
        def reads = LatencyStats.measure(30, 4) {
            api.postRaw('/api/protech/changes/read', 'application/json', read).status == 200
        }
        def lookups = LatencyStats.measure(30, 4) {
            api.get('/api/protech/lookups/users?q=a&limit=20').status == 200
        }

        then:
        [creates, reads, lookups].every { it.errors == 0 }
        creates.p95 < 500
        reads.p95 < 500
        lookups.p95 < 500
    }
}

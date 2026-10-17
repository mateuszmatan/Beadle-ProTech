package com.bbh.itss.beadle.protech.performance

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification
import com.bbh.itss.beadle.protech.support.LatencyStats

class ProTechPerformanceSpec extends BeadleProTechSpecification {

    def "status, lookups and call log respond with low p95 latencies"() {
        when:
        def statusStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/protech/status') {
            api.get('/api/protech/status').status == 200
        }
        def lookupStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/protech/lookups/users') {
            api.get('/api/protech/lookups/users?limit=10').status == 200
        }
        def callLogStats = LatencyStats.measure([calls: 20, threads: 2], 'GET /api/protech/call-log') {
            api.get('/api/protech/call-log?page=0&size=10').status == 200
        }

        then:
        statusStats.p95 < 500
        lookupStats.p95 < 500
        callLogStats.p95 < 600
        statusStats.errors == 0
        lookupStats.errors == 0
        callLogStats.errors == 0
    }
}

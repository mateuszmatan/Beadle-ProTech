package com.bbh.itss.beadle.protech.smoke

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification

class ProTechSmokeSpec extends BeadleProTechSpecification {

    def "the service reports UP on health endpoints"() {
        when:
        def health = anonymous.get('/actuator/health')
        def liveness = anonymous.get('/actuator/health/liveness')
        def readiness = anonymous.get('/actuator/health/readiness')

        then:
        health.status == 200
        health.json.status == 'UP'
        liveness.status == 200
        liveness.json.status == 'UP'
        readiness.status == 200
        readiness.json.status == 'UP'
    }

    def "the service reports status and serves lookup catalogs"() {
        when:
        def status = api.get('/api/protech/status')
        def users = api.get('/api/protech/lookups/users?limit=10')
        def departments = api.get('/api/protech/lookups/departments?limit=10')
        def callLog = api.get('/api/protech/call-log?page=0&size=10')

        then:
        status.status == 200
        status.json.containsKey('connected')
        users.status == 200
        users.json instanceof List
        departments.status == 200
        departments.json instanceof List
        callLog.status == 200
    }
}

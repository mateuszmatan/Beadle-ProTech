package com.bbh.itss.beadle.protech.smoke

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification
import com.bbh.itss.beadle.protech.support.Json

import static java.time.Duration.ofDays

class ProTechSmokeSpec extends BeadleProTechSpecification {

    def "the probes of OpenShift find the service up without a token"() {
        expect:
        ['/actuator/health/liveness', '/actuator/health/readiness'].every {
            def probe = anonymous.get(it)
            probe.status == 200 && probe.json.status == 'UP'
        }
    }

    def "a change is created, read back and looked at through the webhook"() {
        given:
        def start = clock.instant()
        def draft = draft(start, start + ofDays(3))

        when:
        def created = api.postRaw('/api/protech/changes', 'application/json', Json.jackson(draft))
        def known = draft.toBuilder().number(created.json.number as String).build()
        def read = api.postRaw('/api/protech/changes/read', 'application/json', Json.jackson([known]))
        def looked = webhook.post('/api/protech/webhook', [number: known.number()])

        then:
        created.status == 201
        known.number() ==~ /CHG\d{7}/
        read.status == 200
        read.json[known.number()].state == 'DRAFT'
        looked.status == 202
    }
}

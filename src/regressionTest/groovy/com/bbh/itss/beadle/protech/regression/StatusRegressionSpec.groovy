package com.bbh.itss.beadle.protech.regression

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification

class StatusRegressionSpec extends BeadleProTechSpecification {

    def "without beadle.protech.url the demo ProTech plays and says so"() {
        when:
        def response = api.get('/api/protech/status')

        then:
        response.status == 200
        response.header('Content-Type').startsWith('application/json')
        response.json == [connected: false, mode: 'demo']
        callLog(null) == []
    }

    def "a call #without is refused as a problem and the API stays closed to it"() {
        given:
        def client = authorization == null ? anonymous : anonymous.withHeaders([Authorization: authorization])

        when:
        def responses = ['/api/protech/status', '/api/protech/lookups/users', '/api/protech/call-log']
                .collect { client.get(it) }
        def raised = client.post('/api/protech/changes', [shortDescription: 'CertScanner CERT 4.2: Expiry alerts'])

        then:
        (responses + raised).every { it.status == 401 }
        (responses + raised).every { it.header('WWW-Authenticate') == 'Bearer' }
        responses[0].header('Content-Type').startsWith('application/problem+json')
        responses[0].json == [title : 'Unauthorized', status: 401,
                              detail: 'The call needs the Beadle service token as Authorization: Bearer <token>.']

        where:
        without                        | authorization
        'without a token'              | null
        'with a wrong token'           | 'Bearer wrong-token'
        'with the token but no Bearer' | TOKEN
        'with basic credentials'       | 'Basic ' + 'beadle:secret'.bytes.encodeBase64()
    }

    def "the health of the service is open to the platform without a token"() {
        when:
        def health = anonymous.get('/actuator/health')

        then:
        health.status == 200
        health.json.status == 'UP'
    }
}

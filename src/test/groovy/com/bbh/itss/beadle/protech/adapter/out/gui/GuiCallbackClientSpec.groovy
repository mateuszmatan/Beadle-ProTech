package com.bbh.itss.beadle.protech.adapter.out.gui

import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.calllog.CallerContext
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import groovy.json.JsonSlurper
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpStatus.ACCEPTED
import static org.springframework.http.HttpStatus.FOUND
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest

class GuiCallbackClientSpec extends Specification {

    static final String URL = 'http://beadle:8081'
    static final ChangeCallback CALLBACK = new ChangeCallback('CHG0031001', STATE_CHANGED, IMPLEMENTATION,
            Instant.parse('2026-10-15T09:00:00Z'))

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def client = new GuiCallbackClient(new GuiProperties(URL + '/'), ' service-token ', builder)

    def "a moved change is posted with the service token, the system as caller and the correlation id in the header only"() {
        given:
        server.expect(requestTo(URL + '/api/callbacks/protech')).andExpect(method(POST))
                .andExpect(header('Authorization', 'Bearer service-token'))
                .andExpect(header('X-Beadle-User', 'system'))
                .andExpect(correlationId ? header('X-Correlation-Id', correlationId) : headerDoesNotExist('X-Correlation-Id'))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect { ClientHttpRequest request ->
                    assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) ==
                            [number: 'CHG0031001', event: 'STATE_CHANGED', state: 'IMPLEMENTATION',
                             at    : '2026-10-15T09:00:00Z']
                }
                .andRespond(withStatus(ACCEPTED))

        when:
        CallerContext.run(new Caller('system', correlationId)) { client.notify(CALLBACK) }

        then:
        server.verify()

        where:
        correlationId << ['corr-1', null]
    }

    def "a Beadle-GUI answering #problem refuses the callback with the reason"() {
        given:
        server.expect(requestTo(URL + '/api/callbacks/protech')).andRespond(response)

        when:
        client.notify(CALLBACK)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('Beadle-GUI could not take the callback for CHG0031001: ' + reason)

        where:
        problem          | response                                                   || reason
        'a server error' | withServerError()                                          || '500 Internal Server Error'
        'a refusal'      | withUnauthorizedRequest()                                  || '401 Unauthorized'
        'a redirect'     | withStatus(FOUND).header('Location', URL + '/oauth2/ping') || 'Beadle-GUI answered 302 FOUND'
        'nothing'        | withException(new ConnectException('Connection refused'))  || 'I/O error'
    }
}

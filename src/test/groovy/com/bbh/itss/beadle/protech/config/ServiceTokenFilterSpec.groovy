package com.bbh.itss.beadle.protech.config

import groovy.json.JsonSlurper
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.config.ServiceTokenFilter.DETAIL

class ServiceTokenFilterSpec extends Specification {

    def filter = new ServiceTokenFilter(new ServiceTokenProperties(' service-token '))

    def "a call with the service token as a bearer token passes"() {
        given:
        def chain = new MockFilterChain()

        when:
        def response = run(filter, '/api/protech/status', 'Bearer service-token', chain)

        then:
        response.status == 200
        chain.request != null
    }

    def "a call with #authorization is answered 401 with a problem"() {
        given:
        def chain = new MockFilterChain()

        when:
        def response = run(filter, '/api/protech/changes', authorization, chain)

        then:
        response.status == 401
        response.getHeader('WWW-Authenticate') == 'Bearer'
        response.contentType.startsWith('application/problem+json')
        new JsonSlurper().parseText(response.contentAsString) == [title: 'Unauthorized', status: 401, detail: DETAIL]
        chain.request == null

        where:
        authorization << [null, '', 'Bearer', 'Bearer ', 'Bearer other-token', 'Basic c2VydmljZS10b2tlbg==',
                          'service-token', 'bearer service-token']
    }

    def "the health endpoints are open, the rest of the actuator is not"() {
        expect:
        run(filter, path, null, new MockFilterChain()).status == status

        where:
        path                         || status
        '/actuator/health'           || 200
        '/actuator/health/liveness'  || 200
        '/actuator/health/readiness' || 200
        '/actuator/healthy'          || 401
        '/actuator/info'             || 401
    }

    def "without a configured token every call is refused"() {
        given:
        def closed = new ServiceTokenFilter(new ServiceTokenProperties(token))

        expect:
        run(closed, '/api/protech/status', 'Bearer ', new MockFilterChain()).status == 401
        run(closed, '/api/protech/status', 'Bearer x', new MockFilterChain()).status == 401
        !closed.accepts('Bearer ')

        where:
        token << [null, '', '  ']
    }

    private static MockHttpServletResponse run(ServiceTokenFilter filter, String path, String authorization,
                                               MockFilterChain chain) {
        def request = new MockHttpServletRequest('GET', path)
        if (authorization != null) {
            request.addHeader('Authorization', authorization)
        }
        def response = new MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        response
    }
}

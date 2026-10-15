package com.bbh.itss.beadle.protech.config

import com.bbh.itss.beadle.protech.application.calllog.CallerContext
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import groovy.json.JsonSlurper
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

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

    def "a call let in runs as the user and correlation id of its headers, and nobody once it is answered"() {
        given:
        Caller seen = null
        def chain = { ServletRequest request, ServletResponse response -> seen = CallerContext.current() } as FilterChain

        when:
        run(filter, '/api/protech/changes', 'Bearer service-token', chain,
                ['X-Beadle-User': ' Mateusz Matan ', 'X-Correlation-Id': ' corr-1 '])

        then:
        seen == new Caller('Mateusz Matan', 'corr-1')
        CallerContext.current() == Caller.NONE
    }

    def "a call without the headers gets a fresh correlation id and no user"() {
        given:
        List<Caller> seen = []
        def chain = { ServletRequest request, ServletResponse response -> seen << CallerContext.current() } as FilterChain

        when:
        run(filter, '/api/protech/status', 'Bearer service-token', chain)
        run(filter, '/api/protech/status', 'Bearer service-token', chain, ['X-Beadle-User': ' ', 'X-Correlation-Id': ''])

        then:
        seen*.user() == [null, null]
        seen.every { it.correlationId() ==~ /[0-9a-f-]{36}/ }
        seen[0].correlationId() != seen[1].correlationId()
    }

    def "the caller is cleared also when the call fails, and a refused call never runs as anybody"() {
        given:
        Caller seen = Caller.NONE
        def failing = { ServletRequest request, ServletResponse response -> throw new IllegalStateException('boom') } as FilterChain
        def watching = { ServletRequest request, ServletResponse response -> seen = CallerContext.current() } as FilterChain

        when:
        run(filter, '/api/protech/status', 'Bearer service-token', failing, ['X-Correlation-Id': 'corr-1'])

        then:
        thrown(IllegalStateException)
        CallerContext.current() == Caller.NONE

        when:
        run(filter, '/api/protech/status', 'Bearer wrong', watching, ['X-Correlation-Id': 'corr-1'])

        then:
        seen == Caller.NONE
    }

    private static MockHttpServletResponse run(ServiceTokenFilter filter, String path, String authorization,
                                               FilterChain chain, Map<String, String> headers = [:]) {
        def request = new MockHttpServletRequest('GET', path)
        if (authorization != null) {
            request.addHeader('Authorization', authorization)
        }
        headers.each { name, value -> request.addHeader(name, value) }
        def response = new MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        response
    }
}

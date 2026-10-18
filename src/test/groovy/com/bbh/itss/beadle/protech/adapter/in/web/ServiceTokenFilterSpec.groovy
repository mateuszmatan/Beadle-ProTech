package com.bbh.itss.beadle.protech.adapter.in.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.calllog.CallerContext
import groovy.json.JsonSlurper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

import static com.bbh.itss.beadle.protech.adapter.in.web.ServiceTokenFilter.CORRELATION_TOO_LONG
import static com.bbh.itss.beadle.protech.adapter.in.web.ServiceTokenFilter.DETAIL

class ServiceTokenFilterSpec extends Specification {

    def filter = new ServiceTokenFilter(' service-token ', ' webhook-token ')

    def "a call with #authorization is answered 401 with a problem and logged"() {
        given:
        def logged = new ListAppender<ILoggingEvent>()
        logged.start()
        (LoggerFactory.getLogger(ServiceTokenFilter) as Logger).addAppender(logged)
        def chain = new MockFilterChain()

        when:
        def response = run(filter, 'GET', '/api/protech/changes', authorization, chain)

        then:
        response.status == 401
        response.getHeader('WWW-Authenticate') == 'Bearer'
        response.contentType.startsWith('application/problem+json')
        new JsonSlurper().parseText(response.contentAsString) == [title: 'Unauthorized', status: 401, detail: DETAIL]
        chain.request == null
        logged.list*.formattedMessage == ['Refused GET /api/protech/changes: missing or wrong service token']

        cleanup:
        (LoggerFactory.getLogger(ServiceTokenFilter) as Logger).detachAppender(logged)

        where:
        authorization << [null, '', 'Bearer', 'Bearer ', 'Bearer other-token', 'Basic c2VydmljZS10b2tlbg==',
                          'service-token', 'bearer service-token', 'Bearer webhook-token']
    }

    def "#method #uri with #authorization is answered #status"() {
        expect:
        run(filter, method, uri, authorization, new MockFilterChain()).status == status

        where:
        method | uri                                       | authorization          || status
        'GET'  | '/api/protech/status'                     | 'Bearer service-token' || 200
        'POST' | '/api/protech/webhook'                    | 'Bearer service-token' || 200
        'POST' | '/api/protech/webhook'                    | 'Bearer webhook-token' || 200
        'GET'  | '/api/protech/webhook'                    | 'Bearer webhook-token' || 401
        'POST' | '/api/protech/webhook/'                   | 'Bearer webhook-token' || 401
        'POST' | '/api/protech/webhook/../changes'         | 'Bearer webhook-token' || 401
        'POST' | '/api/protech/webhook;x=y'                | 'Bearer webhook-token' || 401
        'POST' | '/api/protech/changes'                    | 'Bearer webhook-token' || 401
        'GET'  | '/actuator/health'                        | null                   || 200
        'GET'  | '/actuator/health/liveness'               | null                   || 200
        'GET'  | '/actuator/health/readiness'              | null                   || 200
        'GET'  | '/actuator/healthy'                       | null                   || 401
        'GET'  | '/actuator/info'                          | null                   || 401
    }

    def "a URI that starts with the health path but leads elsewhere needs the token: #uri"() {
        expect:
        run(filter, 'GET', uri, null, new MockFilterChain()).status == 401

        where:
        uri << ['/actuator/health/../../api/protech/status', '/actuator/health/..;/..;/api/protech/status',
                '/actuator/health/%2e%2e/%2e%2e/api/protech/status', '/actuator/health;x=y',
                '/actuator/health/liveness;jsessionid=1', '/actuator/health//liveness', '/actuator/health/',
                '/actuator/health/..%2f..%2fapi/protech/status', '/actuator/health/.', '/actuator/health\\..\\env']
    }

    def "under a context path only its health endpoints are open and its webhook takes the webhook token"() {
        given:
        def request = new MockHttpServletRequest('POST', uri)
        request.contextPath = '/protech'
        request.addHeader('Authorization', 'Bearer webhook-token')

        expect:
        filter.shouldNotFilter(request) == open
        filter.accepts(request) == webhook

        where:
        uri                                    || open  | webhook
        '/protech/actuator/health/readiness'   || true  | false
        '/protech/actuator/health/../../api'   || false | false
        '/protech/api/protech/webhook'         || false | true
        '/api/protech/webhook'                 || false | false
    }

    def "without configured tokens every call is refused, the webhook too"() {
        given:
        def closed = new ServiceTokenFilter(token, token)

        expect:
        run(closed, 'GET', '/api/protech/status', 'Bearer ', new MockFilterChain()).status == 401
        run(closed, 'POST', '/api/protech/webhook', 'Bearer ', new MockFilterChain()).status == 401

        where:
        token << [null, '', '  ']
    }

    def "a call let in runs as the caller its headers name, with the correlation id in the MDC, and as nobody after"() {
        given:
        Caller seen = null
        String logged = null
        def chain = { ServletRequest request, ServletResponse response ->
            seen = CallerContext.current()
            logged = MDC.get('correlationId')
        } as FilterChain

        when:
        run(filter, 'GET', '/api/protech/changes', 'Bearer service-token', chain,
                ['X-Beadle-User': ' Mateusz Matan ', 'X-Correlation-Id': ' corr-1 '])

        then:
        seen == new Caller('Mateusz Matan', 'corr-1')
        logged == 'corr-1'
        CallerContext.current() == Caller.NONE
        MDC.get('correlationId') == null
    }

    def "the user travels percent-encoded in UTF-8 and is logged decoded, or as unknown without one: #header"() {
        given:
        Caller seen = null
        def chain = { ServletRequest request, ServletResponse response -> seen = CallerContext.current() } as FilterChain

        when:
        run(filter, 'GET', '/api/protech/status', 'Bearer service-token', chain, ['X-Beadle-User': header])

        then:
        seen.user() == user

        where:
        header                                                || user
        '%C5%81ukasz+W%C3%B3jcik'                             || 'Łukasz Wójcik'
        URLEncoder.encode('Żaneta Ćwięk-Ślusarczyk', 'UTF-8') || 'Żaneta Ćwięk-Ślusarczyk'
        'Mateusz Matan'                                       || 'Mateusz Matan'
        '100% sure'                                           || '100% sure'
        '%20'                                                 || 'unknown'
        ' '                                                   || 'unknown'
    }

    def "a call without a correlation id gets a fresh one, and one of #length bytes is #outcome"() {
        given:
        def logged = new ListAppender<ILoggingEvent>()
        logged.start()
        (LoggerFactory.getLogger(ServiceTokenFilter) as Logger).addAppender(logged)
        List<Caller> seen = []
        def chain = { ServletRequest request, ServletResponse answer -> seen << CallerContext.current() } as FilterChain

        when:
        def response = run(filter, 'GET', '/api/protech/status', 'Bearer service-token', chain,
                length == null ? [:] : ['X-Correlation-Id': 'c' * length])

        then:
        response.status == status
        seen*.correlationId().every { length == null ? it ==~ /[0-9a-f-]{36}/ : it == 'c' * length }
        seen.size() == (status == 200 ? 1 : 0)
        status == 200 || new JsonSlurper().parseText(response.contentAsString) == [title: 'Validation failed',
                status: 400, detail: 'X-Correlation-Id ' + CORRELATION_TOO_LONG,
                problems: [[field: 'X-Correlation-Id', message: CORRELATION_TOO_LONG]]]
        logged.list*.formattedMessage == (status == 200 ? [] :
                ["Refused GET /api/protech/status: X-Correlation-Id of $length bytes".toString()])

        cleanup:
        (LoggerFactory.getLogger(ServiceTokenFilter) as Logger).detachAppender(logged)

        where:
        length || status | outcome
        null   || 200    | 'taken'
        64     || 200    | 'taken'
        65     || 400    | 'refused and logged'
    }

    def "the caller is cleared also when the call fails"() {
        given:
        def failing = { ServletRequest request, ServletResponse response -> throw new IllegalStateException('boom') } as FilterChain

        when:
        run(filter, 'GET', '/api/protech/status', 'Bearer service-token', failing, ['X-Correlation-Id': 'corr-1'])

        then:
        thrown(IllegalStateException)
        CallerContext.current() == Caller.NONE
        MDC.get('correlationId') == null
    }

    private static MockHttpServletResponse run(ServiceTokenFilter filter, String method, String uri,
                                               String authorization, FilterChain chain,
                                               Map<String, String> headers = [:]) {
        def request = new MockHttpServletRequest(method, uri)
        if (authorization != null) {
            request.addHeader('Authorization', authorization)
        }
        headers.each { name, value -> request.addHeader(name, value) }
        def response = new MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        response
    }
}

package com.bbh.itss.beadle.protech.domain.calllog

import org.slf4j.MDC
import spock.lang.Specification

import java.util.concurrent.Executors

class CallerContextSpec extends Specification {

    def "the caller is held per thread with its correlation id in the MDC until its scope closes, and nobody by default"() {
        given:
        def caller = new Caller('Mateusz Matan', 'corr-1')
        def other = Executors.newSingleThreadExecutor()

        expect:
        CallerContext.current() == Caller.NONE

        when:
        def scope = CallerContext.open(caller)

        then:
        CallerContext.current() == caller
        MDC.get(CallerContext.MDC_KEY) == 'corr-1'
        other.submit { CallerContext.current() }.get() == Caller.NONE

        when:
        scope.close()

        then:
        CallerContext.current() == Caller.NONE
        MDC.get(CallerContext.MDC_KEY) == null

        cleanup:
        other.shutdown()
    }

    def "work can run as another caller and the previous one comes back afterwards"() {
        given:
        def before = new Caller('Mateusz Matan', 'corr-1')
        def system = new Caller('system', 'tick-1')

        when:
        def seen = CallerContext.as(before) { CallerContext.as(system) { [CallerContext.current(), MDC.get('correlationId')] } }

        then:
        seen == [system, 'tick-1']
        CallerContext.current() == Caller.NONE

        when:
        def scope = CallerContext.open(before)
        CallerContext.as(system) { CallerContext.current() }

        then:
        CallerContext.current() == before
        MDC.get('correlationId') == 'corr-1'

        cleanup:
        scope?.close()
    }

    def "work without a result runs as another caller too, even when it fails"() {
        given:
        def before = new Caller('Mateusz Matan', 'corr-1')
        def system = new Caller('system', 'corr-2')
        Caller seen = null
        def scope = CallerContext.open(before)

        when:
        CallerContext.run(system) { seen = CallerContext.current() }

        then:
        seen == system
        CallerContext.current() == before

        when:
        CallerContext.run(system) { throw new IllegalStateException('boom') }

        then:
        thrown(IllegalStateException)
        CallerContext.current() == before

        cleanup:
        scope.close()
    }
}

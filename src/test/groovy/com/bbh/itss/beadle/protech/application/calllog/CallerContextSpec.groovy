package com.bbh.itss.beadle.protech.application.calllog

import com.bbh.itss.beadle.protech.domain.calllog.Caller
import spock.lang.Specification

import java.util.concurrent.Executors

class CallerContextSpec extends Specification {

    def cleanup() {
        CallerContext.clear()
    }

    def "the caller is held per thread until cleared, and nobody by default"() {
        given:
        def caller = new Caller('Mateusz Matan', 'corr-1')
        def other = Executors.newSingleThreadExecutor()

        expect:
        CallerContext.current() == Caller.NONE

        when:
        CallerContext.set(caller)

        then:
        CallerContext.current() == caller
        other.submit { CallerContext.current() }.get() == Caller.NONE

        when:
        CallerContext.clear()

        then:
        CallerContext.current() == Caller.NONE

        cleanup:
        other.shutdown()
    }

    def "work can run as another caller and the previous one comes back afterwards"() {
        given:
        def before = new Caller('Mateusz Matan', 'corr-1')
        def system = new Caller('system', 'tick-1')
        CallerContext.set(before)

        when:
        def seen = CallerContext.as(system) { CallerContext.current() }

        then:
        seen == system
        CallerContext.current() == before

        when:
        CallerContext.clear()
        CallerContext.as(system) { CallerContext.current() }

        then:
        CallerContext.current() == Caller.NONE
    }

    def "work without a result runs as another caller too, even when it fails"() {
        given:
        def before = new Caller('Mateusz Matan', 'corr-1')
        def system = new Caller('system', 'corr-2')
        Caller seen = null
        CallerContext.set(before)

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
    }
}

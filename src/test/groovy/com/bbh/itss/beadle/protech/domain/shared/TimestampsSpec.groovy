package com.bbh.itss.beadle.protech.domain.shared

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class TimestampsSpec extends Specification {

    def "a timestamp read from a clock keeps microseconds, the precision the database stores"() {
        given:
        def clock = fixed(Instant.parse('2026-10-05T10:15:30.123456789Z'), UTC)

        expect:
        now(clock) == Instant.parse('2026-10-05T10:15:30.123456Z')
    }
}

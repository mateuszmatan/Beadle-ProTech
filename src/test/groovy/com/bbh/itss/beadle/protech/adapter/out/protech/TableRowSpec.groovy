package com.bbh.itss.beadle.protech.adapter.out.protech

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.UPDATED

class TableRowSpec extends Specification {

    static final Instant FALLBACK = Instant.parse('2026-10-18T08:00:00Z')

    def logged = new ListAppender<ILoggingEvent>()

    def setup() {
        logged.start()
        (LoggerFactory.getLogger(TableRow) as Logger).addAppender(logged)
    }

    def cleanup() {
        (LoggerFactory.getLogger(TableRow) as Logger).detachAppender(logged)
    }

    def "a date-time is read in ServiceNow's internal form or as an instant: #value"() {
        expect:
        new TableRow([(UPDATED): [value: value, display_value: '18.10.2026']]).instant(UPDATED, FALLBACK) == expected
        logged.list.isEmpty()

        where:
        value                  || expected
        '2026-10-05 09:00:00'  || Instant.parse('2026-10-05T09:00:00Z')
        '2026-10-05T09:00:00Z' || Instant.parse('2026-10-05T09:00:00Z')
        ' '                    || FALLBACK
    }

    def "a field left out or not mapped reads as the fallback"() {
        expect:
        new TableRow([:]).instant(UPDATED, FALLBACK) == FALLBACK
        new TableRow([(UPDATED): '2026-10-05 09:00:00']).instant('', null) == null
    }

    def "a value that is no date-time reads as the fallback, with one warning naming the field for each value"() {
        when:
        def read = ['05/10/2026 09:00', '05/10/2026 09:00', 'yesterday'].collect {
            new TableRow([(UPDATED): it]).instant(UPDATED, null)
        }

        then:
        read == [null, null, null]
        logged.list*.level == [Level.WARN, Level.WARN]
        logged.list*.formattedMessage == [
                'ProTech answered 05/10/2026 09:00 for sys_updated_on, which is no date-time: it is read as none',
                'ProTech answered yesterday for sys_updated_on, which is no date-time: it is read as none']
    }
}

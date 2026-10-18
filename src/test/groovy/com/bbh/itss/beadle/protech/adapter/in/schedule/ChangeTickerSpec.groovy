package com.bbh.itss.beadle.protech.adapter.in.schedule

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase
import com.bbh.itss.beadle.protech.config.SchedulingConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import spock.lang.Specification

import java.time.Duration
import java.util.function.Supplier

class ChangeTickerSpec extends Specification {

    def "a tick never throws the #failure.class.simpleName the look ran into: it logs it and looks again at the next poll"() {
        given:
        def logged = new ListAppender<ILoggingEvent>()
        logged.start()
        (LoggerFactory.getLogger(ChangeTicker) as Logger).addAppender(logged)
        ChangeWatchUseCase failing = Mock()

        when:
        new ChangeTicker(failing).tick()

        then:
        1 * failing.tick() >> { throw failure }
        logged.list*.formattedMessage == ['The look at the changes in ProTech failed and is tried again at the next poll: '
                                                  + failure.message]

        cleanup:
        (LoggerFactory.getLogger(ChangeTicker) as Logger).detachAppender(logged)

        where:
        failure << [new UncheckedIOException('ProTech could not poll the changes: 503', new IOException()),
                    new IllegalStateException('database down')]
    }

    def "the ticker looks every beadle.protech.poll"() {
        expect:
        new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration, ChangeTicker)
                .withBean(ChangeWatchUseCase, { Mock(ChangeWatchUseCase) } as Supplier<ChangeWatchUseCase>)
                .withPropertyValues('beadle.protech.poll=PT5M').run { context ->
            def task = context.getBean(ScheduledTaskHolder).scheduledTasks*.task.find { it instanceof FixedDelayTask }
            assert task.intervalDuration == Duration.ofMinutes(5)
            assert task.initialDelayDuration == Duration.ofMinutes(5)
        }
    }
}

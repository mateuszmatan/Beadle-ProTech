package com.bbh.itss.beadle.protech.adapter.in.schedule

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase
import com.bbh.itss.beadle.protech.config.SchedulingConfiguration
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.domain.change.WatchReport
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.TASK_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION

class ChangeTickerSpec extends Specification {

    ChangeWatchUseCase watch = Mock()
    def ticker = new ChangeTicker(watch)
    def logged = new ListAppender<ILoggingEvent>()
    def logger = LoggerFactory.getLogger(ChangeTicker) as Logger
    def formerLevel = logger.level

    def setup() {
        logged.start()
        logger.addAppender(logged)
        logger.level = Level.INFO
    }

    def cleanup() {
        logger.detachAppender(logged)
        logger.level = formerLevel
    }

    def "a tick looks at the changes and logs the callbacks it sent and the ones Beadle-GUI did not take"() {
        when:
        ticker.tick()

        then:
        1 * watch.tick() >> report
        noExceptionThrown()
        logged.list.collect { "$it.level $it.formattedMessage" as String } == messages

        where:
        report << [WatchReport.NOTHING,
                   new WatchReport([new ChangeCallback('CHG0031001', TASK_CHANGED, IMPLEMENTATION, Instant.EPOCH, 'corr-1')],
                           [CHG0031002: 'Beadle-GUI could not take the callback for CHG0031002: 503'])]
        messages << [[],
                     ['INFO Told Beadle-GUI that ProTech change CHG0031001 moved: TASK_CHANGED, now IMPLEMENTATION',
                      'WARN Beadle-GUI could not be told that change CHG0031002 moved; it is told again at the next ' +
                              'look: Beadle-GUI could not take the callback for CHG0031002: 503']]
    }

    def "a tick never throws the #failure.class.simpleName the look ran into, it logs it and looks again on the next tick"() {
        when:
        ticker.tick()

        then:
        1 * watch.tick() >> { throw failure }
        noExceptionThrown()
        logged.list.size() == 1
        with(logged.list.first()) {
            level == Level.WARN
            formattedMessage == 'The look at the changes in ProTech failed and is tried again on the next tick: ' +
                    failure.message
            throwableProxy.className == failure.class.name
        }

        where:
        failure << [new UncheckedIOException('ProTech could not poll the changes: 503', new IOException()),
                    new IllegalStateException('database down')]
    }

    def "the ticker looks every #expected #mode"() {
        given:
        def runner = new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfiguration, ChangeTicker)
                .withBean(ChangeWatchUseCase, { [tick: { WatchReport.NOTHING }] as ChangeWatchUseCase } as Supplier<ChangeWatchUseCase>)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            def tasks = context.getBeansOfType(ScheduledTaskHolder).values()*.scheduledTasks.flatten()
            assert tasks.size() == 1
            def task = tasks.first().task as FixedDelayTask
            assert task.intervalDuration == expected
            assert task.initialDelayDuration == expected
        }

        where:
        mode                                  | properties                                                                   || expected
        'by default, demo or live'            | []                                                                           || Duration.ofSeconds(10)
        'whatever the poll of ProTech is'     | ['beadle.protech.url=https://bbh.service-now.com', 'beadle.protech.poll=PT5M'] || Duration.ofSeconds(10)
        'as beadle.protech.tick says'         | ['beadle.protech.tick=PT1S']                                                 || Duration.ofSeconds(1)
    }
}

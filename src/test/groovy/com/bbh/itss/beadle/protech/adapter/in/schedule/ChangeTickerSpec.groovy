package com.bbh.itss.beadle.protech.adapter.in.schedule

import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase
import com.bbh.itss.beadle.protech.config.SchedulingConfiguration
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.domain.change.WatchReport
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

    def "a tick looks at the changes and never throws, whatever the look ran into"() {
        when:
        ticker.tick()

        then:
        1 * watch.tick() >> outcome
        noExceptionThrown()

        where:
        outcome << [WatchReport.NOTHING,
                    new WatchReport([new ChangeCallback('CHG0031001', TASK_CHANGED, IMPLEMENTATION, Instant.EPOCH, 'corr-1')],
                            [CHG0031002: 'Beadle-GUI could not take the callback for CHG0031002: 503']),
                    { throw new UncheckedIOException('ProTech could not poll the changes: 503', new IOException()) },
                    { throw new IllegalStateException('database down') }]
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

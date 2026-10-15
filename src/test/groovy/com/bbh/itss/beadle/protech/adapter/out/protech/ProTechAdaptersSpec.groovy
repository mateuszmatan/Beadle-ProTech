package com.bbh.itss.beadle.protech.adapter.out.protech

import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.config.ClockConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import spock.lang.Specification

import java.time.Duration

class ProTechAdaptersSpec extends Specification {

    def runner = new ApplicationContextRunner().withUserConfiguration(Adapters)

    def "the demo ProTech plays until beadle.protech.url names the real one"() {
        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBeansOfType(ProTechPort).values()*.class == changes
            assert context.getBeansOfType(ProTechLookupPort).values()*.class == lookups
            true
        }

        where:
        properties                                        || changes              | lookups
        []                                                || [DemoProTechAdapter] | [DemoProTechLookups]
        ['beadle.protech.url=']                           || [DemoProTechAdapter] | [DemoProTechLookups]
        ['beadle.protech.url=https://bbh.service-now.com'] || []                   | []
    }

    def "the ProTech properties take the address, the account, the poll and the demo's apply delay"() {
        expect:
        runner.withPropertyValues('beadle.protech.url=https://bbh.service-now.com', 'beadle.protech.username=beadle',
                'beadle.protech.password=secret', 'beadle.protech.poll=PT30S', 'beadle.protech.demo.apply-delay=PT1S')
                .run { context ->
                    assert context.getBean(ProTechProperties) ==
                            new ProTechProperties('https://bbh.service-now.com', 'beadle', 'secret', Duration.ofSeconds(30))
                    assert context.getBean(DemoProTechProperties) == new DemoProTechProperties(Duration.ofSeconds(1))
                    true
                }
        runner.run { context ->
            assert context.getBean(ProTechProperties) == new ProTechProperties(null, null, null, Duration.ofMinutes(1))
            assert context.getBean(DemoProTechProperties) == new DemoProTechProperties(Duration.ofSeconds(3))
            true
        }
    }

    @EnableConfigurationProperties([ProTechProperties, DemoProTechProperties])
    @Import([ClockConfiguration, DemoProTechAdapter, DemoProTechLookups])
    static class Adapters {
    }
}

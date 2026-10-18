package com.bbh.itss.beadle.protech.config

import com.bbh.itss.beadle.protech.support.EnvironmentVariables
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.support.EnvironmentVariables.SHARED_WITH_GUI
import static org.springframework.boot.WebApplicationType.NONE

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/BEADLE',
                               DB_USERNAME: 'BEADLE_PROTECH', DB_PASSWORD: 'secret']
    static final Map REAL_PROTECH = [PROTECH_URL: 'https://protech.bbh.test', PROTECH_USERNAME: 'beadle',
                                     PROTECH_PASSWORD: 'secret']

    def "without a profile Beadle-ProTech runs locally on port 8083 on H2 in the home folder with the demo ProTech"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.defaultProfiles as List == ['local']
        env.getProperty('spring.application.name') == 'beadle-protech'
        env.getProperty('server.port') == '8083'
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/bbh-devsecops/beadle-protech/beadle-protech;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        environment([], [BEADLE_PROTECH_DATA_DIR: '/var/beadle']).getProperty('spring.datasource.url')
                .startsWith('jdbc:h2:file:/var/beadle/beadle-protech;')
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.change-log') == 'classpath:db/changelog/db.changelog-master.yaml'
        env.getProperty('beadle.protech.instance-url') == ''
        env.getProperty('beadle.protech.poll') == 'PT10S'
        env.getProperty('beadle.protech.demo.apply-delay') == 'PT3S'
        env.getProperty('beadle.gui.url') == 'http://localhost:8081'
        env.getProperty('beadle.services.token') == ''
        env.getProperty('spring.http.clients.connect-timeout') == '5s'
        env.getProperty('spring.http.clients.read-timeout') == '10s'
        env.getProperty('management.endpoint.health.show-details') == 'always'
    }

    def "the environment names the ProTech, the GUI and the service token"() {
        when:
        def env = environment([], [PROTECH_URL: 'https://protech.bbh.test', PROTECH_USERNAME: 'beadle',
                                   PROTECH_PASSWORD: 'secret', PROTECH_POLL: 'PT30S', BEADLE_GUI_URL: 'http://beadle:8081',
                                   BEADLE_SERVICE_TOKEN: 'token', PORT: '9090'])

        then:
        env.getProperty('beadle.protech.instance-url') == 'https://protech.bbh.test'
        env.getProperty('beadle.protech.username') == 'beadle'
        env.getProperty('beadle.protech.password') == 'secret'
        env.getProperty('beadle.protech.poll') == 'PT30S'
        env.getProperty('beadle.gui.url') == 'http://beadle:8081'
        env.getProperty('beadle.services.token') == 'token'
        env.getProperty('server.port') == '9090'
    }

    def "the #profile profile runs on the Oracle database given by the environment"() {
        when:
        def env = environment([profile], ORACLE + variables)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        env.getProperty('spring.datasource.url') == ORACLE.DB_URL
        env.getProperty('spring.datasource.username') == 'BEADLE_PROTECH'
        env.getProperty('spring.datasource.password') == 'secret'
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == healthDetails
        env.getProperty('logging.level.com.bbh.itss.beadle') == logLevel
        env.getProperty('beadle.protech.poll') == 'PT1M'

        where:
        profile | variables            || poolSize | healthDetails | logLevel
        'rd'    | [:]                  || '5'      | 'never'       | 'DEBUG'
        'qc'    | [:]                  || '10'     | 'never'       | 'INFO'
        'prod'  | [:]                  || '20'     | 'never'       | 'INFO'
        'prod'  | [DB_POOL_SIZE: '40'] || '40'     | 'never'       | 'INFO'
    }

    def "the #profile profile does not start without the database address"() {
        when:
        environment([profile], [:]).getProperty('spring.datasource.url')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('DB_URL')

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "the #profile profile starts on the real ProTech"() {
        when:
        def env = environment([profile], ORACLE + REAL_PROTECH, RealProTechConfiguration)

        then:
        env.getProperty('beadle.protech.instance-url') == 'https://protech.bbh.test'

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "the #profile profile does not start while PROTECH_URL is #blank, since the demo ProTech would answer in its place"() {
        when:
        environment([profile], ORACLE + variables, RealProTechConfiguration)

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith('The rd, qc and prod profiles need the real ProTech, but PROTECH_URL is blank')

        where:
        profile | variables                                 || blank
        'prod'  | [:]                                       || 'missing'
        'qc'    | [PROTECH_URL: '']                         || 'empty'
        'rd'    | [PROTECH_URL: ' ']                        || 'blank'
        'prod'  | SHARED_WITH_GUI                           || "missing, whatever Beadle-GUI's BEADLE_PROTECH_URL says"
    }

    def "the local profile runs on the demo ProTech without asking"() {
        when:
        def env = environment([], [:], RealProTechConfiguration)

        then:
        env.defaultProfiles as List == ['local']
        env.getProperty('beadle.protech.instance-url') == ''
    }

    private static Environment environment(List<String> profiles, Map<String, String> variables,
                                           Class<?> configuration = Empty) {
        def application = new SpringApplicationBuilder(configuration)
                .web(NONE)
                .logStartupInfo(false)
                .environment(EnvironmentVariables.environment(variables))
        if (profiles) {
            application.profiles(profiles as String[])
        }
        def context = application.run()
        def environment = context.environment
        context.close()
        environment
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}

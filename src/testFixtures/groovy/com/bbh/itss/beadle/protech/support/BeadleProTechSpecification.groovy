package com.bbh.itss.beadle.protech.support

import com.bbh.itss.beadle.protech.domain.change.ChangeSchedule
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.time.MutableClock

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.SECONDS
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * Runs Beadle-ProTech on a random port with the demo ProTech on an in-memory H2 in Oracle mode, a clock that only
 * moves when a spec moves it, a look at ProTech every 0.3 s and {@link GuiCallbacks} as Beadle-GUI. The environment variables
 * are those of a shell shared with Beadle-GUI ({@link EnvironmentVariables#SHARED_WITH_GUI}), which must leave the
 * demo ProTech playing. Every spec shares the one application and its clock, so a spec tells its data apart by the
 * correlation id of its calls or by the number of its changes, and only ever moves the clock forward.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-protech;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000',
        'beadle.services.token=regression-service-token',
        'beadle.protech.webhook-token=regression-webhook-token',
        'beadle.gui.url=http://beadle-gui.test:8081',
        'beadle.protech.poll=PT0.3S'])
@ActiveProfiles('local')
@ContextConfiguration(initializers = EnvironmentVariables)
@Import(Regression)
abstract class BeadleProTechSpecification extends Specification {

    static final String TOKEN = 'regression-service-token'
    static final String WEBHOOK_TOKEN = 'regression-webhook-token'
    /** The user signed in to Beadle-GUI, sent percent-encoded as Beadle-GUI sends it. */
    static final String USER = 'Łukasz Wójcik'

    @Value('${local.server.port}')
    int port

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    MutableClock clock

    @Autowired
    GuiCallbacks gui

    String correlationId = UUID.randomUUID().toString()

    /** Long enough for a few looks of the watch, which runs every 0.3 s. */
    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.2)

    ServiceClient getApi() {
        anonymous.withHeaders([Authorization: "Bearer $TOKEN" as String, 'X-Beadle-User': URLEncoder.encode(USER, 'UTF-8'),
                               'X-Correlation-Id': correlationId])
    }

    /** ProTech calling the webhook, with the token it holds for it. */
    ServiceClient getWebhook() {
        anonymous.withHeaders([Authorization: "Bearer $WEBHOOK_TOKEN" as String])
    }

    ServiceClient getAnonymous() {
        new ServiceClient("http://localhost:$port")
    }

    /** The call log of this spec's calls to ProTech, oldest first. */
    List<Map> callLog(String system = 'PROTECH', String correlation = correlationId) {
        def response = api.get("/api/protech/call-log?correlationId=$correlation&size=500")
        assert response.status == 200: response
        (response.json.entries as List<Map>).findAll { system == null || it.system == system }.reverse()
    }

    /** The row Beadle-ProTech keeps of a change it follows, or {@code null}. */
    Map tracked(String number) {
        jdbc.queryForList('SELECT STATE, CORRELATION_ID, LAST_SEEN_AT, LAST_EVENT_AT FROM BEADLE_PROTECH_CHANGE WHERE CHANGE_NUMBER = ?',
                number).find()
    }

    /**
     * A draft of CertScanner CERT 4.2 as Beadle-GUI raises it: created at {@code createdAt}, installed from
     * {@code installationStart} for two hours and validated for one, its tasks created one by one after the raise.
     */
    static ProductionChange draft(Instant createdAt, Instant installationStart) {
        raised(number: null, schedule: scheduleFrom(installationStart), tasks: [],
                workflow: [new WorkflowStep(DRAFT, createdAt)], syncedAt: null, createdAt: createdAt)
    }

    static ChangeSchedule scheduleFrom(Instant installationStart) {
        new ChangeSchedule(installationStart, installationStart + Duration.ofHours(2),
                installationStart + Duration.ofHours(2), installationStart + Duration.ofHours(3),
                installationStart + Duration.ofHours(15), null, null)
    }

    /** Raises {@code draft} in the demo ProTech and answers the number it got. */
    String raise(ProductionChange draft) {
        def raised = api.postRaw('/api/protech/changes', 'application/json', Json.jackson(draft))
        assert raised.status == 201: raised
        raised.json.number
    }

    /** Creates the task {@code index} of the fixtures on change {@code number} and answers its number. */
    String createTask(String number, int index) {
        def created = api.postRaw("/api/protech/changes/$number/tasks", 'application/json',
                Json.jackson(task(index).withNumber(null)))
        assert created.status == 201: created
        created.json.number
    }

    /** The change as ProTech holds it now, read the way Beadle-GUI reads it: by the copy it knows. */
    Map read(ProductionChange known) {
        def response = api.postRaw('/api/protech/changes/read', 'application/json', Json.jackson([known]))
        assert response.status == 200: response
        assert response.json.keySet() == [known.number()] as Set
        response.json[known.number()] as Map
    }

    /** Moves the shared clock forward to {@code at}, never back. */
    void moveTo(Instant at) {
        if (at.isAfter(clock.instant())) {
            clock.instant = at
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Regression {

        @Bean
        @Primary
        MutableClock regressionClock() {
            new MutableClock(Instant.now().truncatedTo(SECONDS), UTC)
        }

        @Bean
        GuiCallbacks guiCallbacks() {
            new GuiCallbacks()
        }

        @Bean
        RestClient.Builder guiRestClientBuilder(GuiCallbacks callbacks) {
            callbacks.builder
        }
    }
}

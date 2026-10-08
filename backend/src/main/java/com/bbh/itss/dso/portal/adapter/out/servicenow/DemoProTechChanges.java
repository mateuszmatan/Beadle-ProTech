package com.bbh.itss.dso.portal.adapter.out.servicenow;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing;
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate;
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.APPLIED;
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.NOT_APPLIED;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.APPLY_LIMIT;
import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.naturalOrder;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
@RequiredArgsConstructor
@Slf4j
class DemoProTechChanges {

    static final List<Scene> SCENES = List.of(
            new Scene(ofDays(12), ofDays(-8), null),
            new Scene(ofDays(10), ofDays(-7), null),
            new Scene(ofDays(9), ofDays(-6), null),
            new Scene(ofDays(4), ofHours(-1), NOT_APPLIED),
            new Scene(ofDays(3), ofDays(2), APPLIED),
            new Scene(ofDays(2), ofDays(4), null),
            new Scene(ofMinutes(30), ofHours(20), null),
            new Scene(ofMinutes(9), ofDays(5), null),
            new Scene(ofMinutes(7), ofDays(6), null),
            new Scene(ofMinutes(5), ofDays(6), null),
            new Scene(ofMinutes(3), ofDays(7), null),
            new Scene(ofMinutes(1), ofDays(8), null));
    static final List<String> MOVED_SCHEDULE = List.of("schedule.installationEnd", "schedule.validationStart",
            "schedule.validationEnd", "schedule.firstUsage");

    private final ProductsUseCase products;
    private final ChangeProfilesUseCase profiles;
    private final ProductionChangesUseCase changes;
    private final ProductionChangeRepositoryPort repository;
    private final DemoServiceNowAdapter serviceNow;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    void store() {
        List<ProductSummaryView> catalogue = products.list(null).stream()
                .sorted(comparing(ProductSummaryView::departmentName, nullsLast(naturalOrder()))
                        .thenComparing(ProductSummaryView::name)).toList();
        if (catalogue.isEmpty() || !changes.list(null).isEmpty()) {
            return;
        }
        Instant now = now(clock);
        for (int index = 0; index < SCENES.size(); index++) {
            repository.save(staged(catalogue.get(index % catalogue.size()), SCENES.get(index), now));
        }
        log.info("Stored {} demo ProTech change(s)", SCENES.size());
    }

    private ProductionChange staged(ProductSummaryView product, Scene scene, Instant now) {
        ProductionChange draft = drafted(product, scene, now).raisedAt(now.minus(scene.raisedAgo()));
        RaisedChange raised = serviceNow.raise(draft);
        ProductionChange numbered = draft.numbered(raised.number(), raised.taskNumbers(), raised.url());
        ProductionChange synced = numbered.synced(serviceNow.read(List.of(numbered)).get(raised.number()), now);
        return synced.toBuilder().update(scene.update() == null ? null : updateOf(scene.update(), synced)).build();
    }

    private ProductionChange drafted(ProductSummaryView product, Scene scene, Instant now) {
        ChangeProfileView profile = profiles.get(product.id());
        List<JiraVersion> versions = changes.versions(product.id(), null);
        String fixVersion = versions.stream().filter(version -> version.released() == scene.startIn().isNegative())
                .findFirst().orElse(versions.getFirst()).name();
        List<String> epicKeys = changes.epics(product.id(), fixVersion, null).stream().limit(2)
                .map(JiraIssue::key).toList();
        List<String> storyKeys = changes.stories(product.id(), fixVersion, epicKeys, null).stream()
                .map(JiraIssue::key).toList();
        ChangeSchedule schedule = scheduleOf(now.plus(scene.startIn()).truncatedTo(MINUTES),
                profile.template().timing());
        return changes.preview(new ChangeCommand(product.id(), fixVersion, epicKeys, storyKeys, schedule,
                profile.template(), profile.tasks(), null, null));
    }

    static ChangeSchedule scheduleOf(Instant start, Timing timing) {
        Instant end = start.plus(ofHours(timing.installationHours()));
        Instant validated = end.plus(ofHours(timing.validationHours()));
        return new ChangeSchedule(start, end, end, validated, validated.plus(ofHours(12)));
    }

    static ChangeUpdate updateOf(Status status, ProductionChange change) {
        if (status == APPLIED) {
            Instant requested = change.createdAt().plus(ofHours(5));
            return new ChangeUpdate(APPLIED, requested, change.departmentName(), List.of(), null,
                    requested.plusSeconds(3));
        }
        Instant requested = change.schedule().installationStart().plus(ofMinutes(30));
        return new ChangeUpdate(NOT_APPLIED, requested, change.departmentName(), MOVED_SCHEDULE, NOT_APPLIED_MESSAGE,
                requested.plus(APPLY_LIMIT));
    }

    record Scene(Duration raisedAgo, Duration startIn, Status update) {
    }
}

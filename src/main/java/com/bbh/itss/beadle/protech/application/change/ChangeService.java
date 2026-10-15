package com.bbh.itss.beadle.protech.application.change;

import com.bbh.itss.beadle.protech.application.UseCase;
import com.bbh.itss.beadle.protech.application.WithoutTransaction;
import com.bbh.itss.beadle.protech.application.calllog.CallerContext;
import com.bbh.itss.beadle.protech.application.change.port.in.ChangesUseCase;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ProTechStatus;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.protech.domain.change.ChangeSchedule.UNPLANNED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTask.validateTasks;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.checkTemplateAndSchedule;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Checks what Beadle-GUI sends before it reaches ProTech, so that a change ProTech would refuse is refused here
 * with the fields at fault, and passes the rest on. ProTech is the system of record of a change and Beadle-GUI keeps
 * its own copy; Beadle-ProTech only follows the changes Beadle-GUI raised or read, in the state Beadle-GUI knows of,
 * so that it can tell Beadle-GUI when ProTech moves them.
 */
@UseCase
@RequiredArgsConstructor
public class ChangeService implements ChangesUseCase {

    public static final String ONE_OF_ROLE_OR_TASK = "name either the role of the change approver or the change task";

    private final ProTechPort proTech;
    private final TrackedChangePort tracked;
    private final Clock clock;

    @Override
    @WithoutTransaction
    public ProTechStatus status() {
        return new ProTechStatus(proTech.connected(), proTech.mode());
    }

    @Override
    @WithoutTransaction
    public RaisedChange raise(ProductionChange draft) {
        ValidationProblems problems = new ValidationProblems();
        check(draft, problems);
        problems.throwIfAny();
        RaisedChange raised = proTech.raise(draft);
        tracked.track(List.of(TrackedChange.seen(raised.number(), DRAFT, now(clock), correlationId())));
        return raised;
    }

    @Override
    @WithoutTransaction
    public String createTask(String changeNumber, ChangeTask task) {
        ValidationProblems problems = new ValidationProblems();
        task.validate(UNPLANNED, problems);
        problems.throwIfAny();
        return proTech.createTask(numbered(changeNumber), task);
    }

    @Override
    @WithoutTransaction
    public Map<String, ProductionChange> read(List<ProductionChange> known) {
        List<ProductionChange> listed = emptyIfNull(known);
        ValidationProblems problems = new ValidationProblems();
        for (int index = 0; index < listed.size(); index++) {
            ProductionChange change = listed.get(index);
            problems.require("[" + index + "].number", change == null ? null : trimToNull(change.number()), REQUIRED);
        }
        problems.throwIfAny();
        if (listed.isEmpty()) {
            return Map.of();
        }
        Map<String, ProductionChange> current = proTech.read(listed);
        Instant now = now(clock);
        String correlationId = correlationId();
        tracked.track(current.entrySet().stream()
                .map(change -> TrackedChange.seen(change.getKey(), change.getValue().state(), now, correlationId))
                .toList());
        return current;
    }

    @Override
    @WithoutTransaction
    public void update(String number, ProductionChange change) {
        ValidationProblems problems = new ValidationProblems();
        String path = numbered(number);
        if (change.number() != null && !change.number().equals(path)) {
            problems.add("number", "must be " + path + ", the change of the path");
        }
        check(change, problems);
        problems.throwIfAny();
        proTech.update(change.number() == null ? change.numbered(path, change.url()) : change);
    }

    @Override
    @WithoutTransaction
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        if ((approval.role() == null) == (trimToNull(approval.task()) == null)) {
            throw InvalidRequestException.of("task", ONE_OF_ROLE_OR_TASK);
        }
        return proTech.remind(numbered(changeNumber), new ApprovalRef(approval.role(), trimToNull(approval.task())));
    }

    private static void check(ProductionChange change, ValidationProblems problems) {
        problems.require("shortDescription", trimToNull(change.shortDescription()), REQUIRED)
                .fits("shortDescription", change.shortDescription(), SHORT_DESCRIPTION_MAX)
                .require("description", trimToNull(change.description()), REQUIRED)
                .fits("description", change.description(), DESCRIPTION_MAX);
        checkTemplateAndSchedule(change.template(), change.schedule(), null, problems);
        validateTasks(change.tasks(), getIfNull(change.schedule(), UNPLANNED), problems);
    }

    private static String correlationId() {
        return CallerContext.current().correlationId();
    }

    private static String numbered(String changeNumber) {
        String number = trimToNull(changeNumber);
        if (number == null) {
            throw InvalidRequestException.of("number", REQUIRED);
        }
        return number;
    }
}

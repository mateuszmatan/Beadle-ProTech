package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.TaskDetails.validateEach;
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN;
import static java.time.Duration.ofMinutes;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeTask(String number, TaskDetails details, Instant start, String approval, TaskState state) {

    public static final String NOT_YET_REQUESTED = "Not Yet Requested";
    public static final Duration START_DELAY = ofMinutes(1);

    public ChangeTask {
        number = trimToNull(number);
        details = getIfNull(details, () -> TaskDetails.builder().build());
        start = details.releaseManagement() ? start : null;
        approval = getIfNull(trimToNull(approval), NOT_YET_REQUESTED);
        state = getIfNull(state, OPEN);
    }

    public static ChangeTask of(TaskDetails details) {
        return new ChangeTask(null, details, null, null, OPEN);
    }

    static void validateTasks(List<ChangeTask> tasks, ChangeSchedule schedule, ValidationProblems problems) {
        validateEach(tasks, problems, (task, at) -> task.validate(schedule, at));
    }

    public ChangeTask numbered(String number) {
        return new ChangeTask(number, details, start, approval, state);
    }

    public ChangeTask in(TaskState state) {
        return new ChangeTask(number, details, start, approval, state);
    }

    public ChangeTask approved(String approval) {
        return new ChangeTask(number, details, start, approval, state);
    }

    public ChangeTask editedTo(ChangeTask edit) {
        return new ChangeTask(number, edit.details, edit.start, approval, state);
    }

    Content content() {
        return new Content(details, start);
    }

    ChangeTask plannedIn(ChangeSchedule schedule, String configurationItem) {
        Instant earliest = schedule.installationStart() == null ? null : schedule.installationStart().plus(START_DELAY);
        return new ChangeTask(number, details.within(configurationItem), getIfNull(start, earliest), approval, state);
    }

    private void validate(ChangeSchedule schedule, ValidationProblems problems) {
        details.validate(problems.at("details"));
        if (!details.releaseManagement()) {
            return;
        }
        problems.require("start", start, "choose when the task starts");
        Instant installationStart = schedule.installationStart();
        if (start != null && installationStart != null && start.isBefore(installationStart.plus(START_DELAY))) {
            problems.add("start", "must be at least a minute after the installation start");
        }
        if (start != null && schedule.installationEnd() != null && start.isAfter(schedule.installationEnd())) {
            problems.add("start", "must not be after the installation end");
        }
    }

    record Content(TaskDetails details, Instant start) {
    }
}

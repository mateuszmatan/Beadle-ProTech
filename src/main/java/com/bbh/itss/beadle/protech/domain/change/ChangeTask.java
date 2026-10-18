package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.With;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.Reminder.people;
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.MAX_TASKS;
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED;
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN;
import static java.time.Duration.ofMinutes;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@With
public record ChangeTask(String number, TaskDetails details, Instant start, ApprovalState approval,
                         List<String> approvers, Reminder reminder, TaskState state) {

    public static final Duration START_DELAY = ofMinutes(1);

    public ChangeTask {
        number = trimToNull(number);
        details = getIfNull(details, () -> TaskDetails.builder().build());
        start = details.releaseManagement() ? start : null;
        approval = getIfNull(approval, NOT_APPROVED);
        approvers = people(approvers);
        reminder = reminder == null || reminder.sentAt() == null ? null : reminder;
        state = getIfNull(state, OPEN);
    }

    public static void validateTasks(List<ChangeTask> tasks, ChangeSchedule schedule, ValidationProblems problems) {
        if (tasks.size() > MAX_TASKS) {
            problems.add("tasks", "may list at most " + MAX_TASKS + " change tasks");
        }
        for (int index = 0; index < tasks.size(); index++) {
            tasks.get(index).validate(schedule, problems.at("tasks[" + index + "]"));
        }
    }

    public boolean active() {
        return state != CANCELED;
    }

    public void validate(ChangeSchedule schedule, ValidationProblems problems) {
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
}

package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.domain.change.ChangeSchedule.UNPLANNED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTask.validateTasks;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.REQUIRED;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * A production change as Beadle-GUI sends it, the {@code Change} of docs/CONTRACTS.md. Beadle-ProTech writes and
 * reads the fields ProTech holds and hands every other field back as it came.
 */
@Builder(toBuilder = true)
public record ProductionChange(Long id, String number, Long productId, String productCode, String productName,
                               Long businessServiceId, String businessServiceName, String openedBy, String fixVersion,
                               ChangeSchedule schedule, String shortDescription, String description,
                               ChangeTemplate template, List<String> epicKeys, List<String> storyKeys,
                               List<ChangeTask> tasks, String url, ChangeState state, List<WorkflowStep> workflow,
                               List<ChangeApproval> approvals, Instant syncedAt, String syncProblem,
                               ChangeUpdate update, Long version, Long editedVersion, Instant createdAt) {

    public static final int SHORT_DESCRIPTION_MAX = 160;
    public static final int DESCRIPTION_MAX = 4000;

    public ProductionChange {
        epicKeys = List.copyOf(emptyIfNull(epicKeys));
        storyKeys = List.copyOf(emptyIfNull(storyKeys));
        tasks = List.copyOf(emptyIfNull(tasks));
        state = getIfNull(state, DRAFT);
        workflow = List.copyOf(emptyIfNull(workflow));
        approvals = List.copyOf(emptyIfNull(approvals));
        editedVersion = getIfNull(editedVersion, version);
    }

    public ProductionChange numbered(String number, String url) {
        return toBuilder().number(number).url(url).build();
    }

    public ProductionChange withTasks(List<ChangeTask> created) {
        return toBuilder().tasks(Stream.concat(tasks.stream(), created.stream()).toList()).build();
    }

    /** Adds what ProTech would refuse the change for: its texts, its ProTech fields, its schedule and its tasks. */
    public void validate(ValidationProblems problems) {
        problems.require("shortDescription", trimToNull(shortDescription), REQUIRED)
                .fits("shortDescription", shortDescription, SHORT_DESCRIPTION_MAX)
                .require("description", trimToNull(description), REQUIRED)
                .fits("description", description, DESCRIPTION_MAX)
                .require("template", template, "fill in the ProTech fields of the change");
        if (template != null) {
            template.validate(problems.at("template"));
        }
        problems.require("schedule", schedule, "choose when the change is installed, validated and first used");
        if (schedule != null) {
            schedule.check(template != null && template.downtime(), problems.at("schedule"));
        }
        validateTasks(tasks, getIfNull(schedule, UNPLANNED), problems);
    }
}

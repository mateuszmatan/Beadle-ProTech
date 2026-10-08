package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED;
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED;
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN;
import static com.bbh.itss.dso.portal.domain.change.TaskText.validateTasks;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static java.time.Duration.ofMinutes;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record ProductionChange(Long id, String number, Long productId, String productCode, String productName,
                               Long departmentId, String departmentName, String fixVersion, ChangeSchedule schedule,
                               String shortDescription, String description, ChangeTemplate template,
                               List<String> epicKeys, List<String> storyKeys, List<ChangeTask> tasks, String url,
                               ChangeState state, List<WorkflowStep> workflow, Instant syncedAt, String syncProblem,
                               ChangeUpdate update, Long version, Long editedVersion, Instant createdAt) {

    public static final int FIX_VERSION_MAX = 100;
    public static final int SHORT_DESCRIPTION_MAX = 160;
    public static final int DESCRIPTION_MAX = 4000;
    public static final Duration APPLY_LIMIT = ofMinutes(1);
    static final int SECTION_MAX = 300;

    public ProductionChange {
        epicKeys = List.copyOf(epicKeys);
        storyKeys = List.copyOf(storyKeys);
        tasks = List.copyOf(tasks);
        state = getIfNull(state, DRAFT);
        workflow = List.copyOf(emptyIfNull(workflow));
        editedVersion = getIfNull(editedVersion, version);
    }

    public static ProductionChange draft(Product product, Long departmentId, String departmentName,
                                         List<TaskText> tasks, String fixVersion, ChangeSchedule schedule,
                                         ChangeTemplate template, List<JiraIssue> epics, List<JiraIssue> stories,
                                         String shortDescription, String description) {
        ChangeTemplate raised = template.releasedAs(fixVersion);
        String summary = isBlank(shortDescription) ? shortDescriptionOf(product, fixVersion, epics)
                : shortDescription.trim();
        String text = isBlank(description)
                ? descriptionOf(product, departmentName, tasks, fixVersion, schedule, raised, epics, stories)
                : description.trim();
        return builder().productId(product.id()).productCode(product.code()).productName(product.name())
                .departmentId(departmentId).departmentName(departmentName).fixVersion(fixVersion).schedule(schedule)
                .shortDescription(summary).description(text).template(raised)
                .epicKeys(epics.stream().map(JiraIssue::key).toList())
                .storyKeys(stories.stream().map(JiraIssue::key).toList())
                .tasks(tasks.stream().map(ChangeTask::of).toList()).build();
    }

    public ProductionChange raisedAt(Instant at) {
        return toBuilder().state(DRAFT).workflow(List.of(new WorkflowStep(DRAFT, at))).syncedAt(at).createdAt(at)
                .build();
    }

    public ProductionChange numbered(String number, List<String> taskNumbers, String url) {
        List<ChangeTask> numberedTasks = range(0, tasks.size())
                .mapToObj(index -> tasks.get(index).numbered(taskNumbers.get(index))).toList();
        return toBuilder().number(number).tasks(numberedTasks).url(url).build();
    }

    public ProductionChange edited(String shortDescription, String description, ChangeSchedule schedule,
                                   ChangeTemplate template, List<ChangeTask> tasks, Instant now) {
        ValidationProblems problems = new ValidationProblems();
        String summary = trimToNull(shortDescription);
        String text = trimToNull(description);
        problems.require("shortDescription", summary, REQUIRED).fits("shortDescription", summary, SHORT_DESCRIPTION_MAX)
                .require("description", text, REQUIRED).fits("description", text, DESCRIPTION_MAX);
        ChangeTemplate edited = template == null ? null : this.template.edited(template).releasedAs(fixVersion);
        boolean moved = schedule != null
                && !Objects.equals(schedule.installationStart(), this.schedule.installationStart());
        checkTemplateAndSchedule(edited, schedule, moved ? now : null, problems);
        List<ChangeTask> requested = editedTasks(tasks, problems);
        problems.throwIfAny();
        return toBuilder().shortDescription(summary).description(text).schedule(schedule).template(edited)
                .tasks(requested).build();
    }

    public static void checkTemplateAndSchedule(ChangeTemplate template, ChangeSchedule schedule, Instant upcomingFrom,
                                                ValidationProblems problems) {
        problems.require("template", template, "fill in the ProTech fields of the change");
        if (template != null) {
            template.validate(problems.at("template"));
        }
        problems.require("schedule", schedule, "choose when the change is installed, validated and first used");
        if (schedule != null) {
            schedule.check(problems.at("schedule"));
            if (upcomingFrom != null) {
                schedule.checkUpcoming(upcomingFrom, problems.at("schedule"));
            }
        }
    }

    public List<String> unappliedIn(ProductionChange remote) {
        List<String> paths = new ArrayList<>();
        ChangeSchedule theirs = remote.schedule;
        ChangeTemplate fields = remote.template;
        differs(paths, "shortDescription", shortDescription, remote.shortDescription);
        differs(paths, "description", description, remote.description);
        differs(paths, "schedule.installationStart", schedule.installationStart(), theirs.installationStart());
        differs(paths, "schedule.installationEnd", schedule.installationEnd(), theirs.installationEnd());
        differs(paths, "schedule.validationStart", schedule.validationStart(), theirs.validationStart());
        differs(paths, "schedule.validationEnd", schedule.validationEnd(), theirs.validationEnd());
        differs(paths, "schedule.firstUsage", schedule.firstUsage(), theirs.firstUsage());
        differs(paths, "template.assignmentGroup", template.assignmentGroup(), fields.assignmentGroup());
        differs(paths, "template.category", template.category(), fields.category());
        differs(paths, "template.configurationItem", template.configurationItem(), fields.configurationItem());
        differs(paths, "template.release", template.release(), fields.release());
        differs(paths, "template.incident", template.incident(), fields.incident());
        differs(paths, "template.problem", template.problem(), fields.problem());
        differs(paths, "template.affectedClients", template.affectedClients(), fields.affectedClients());
        differs(paths, "template.description", template.description(), fields.description());
        differs(paths, "template.approvers", template.approvers(), fields.approvers());
        differs(paths, "template.downtime", template.downtime(), fields.downtime());
        differs(paths, "template.planning", template.planning(), fields.planning());
        differs(paths, "template.privilegedAccess", template.privilegedAccess(), fields.privilegedAccess());
        differs(paths, "template.riskAssessment", template.riskAssessment(), fields.riskAssessment());
        differs(paths, "tasks", texts(), remote.texts());
        return paths;
    }

    public ProductionChange synced(ProductionChange remote, Instant now) {
        ChangeUpdate checked = update == null ? null : update.checked(unappliedIn(remote), now);
        boolean waiting = checked != null && checked.pending();
        ProductionChange values = waiting ? this : remote;
        return toBuilder().shortDescription(values.shortDescription).description(values.description)
                .schedule(values.schedule).template(template.edited(values.template))
                .tasks(waiting ? tasksIn(remote) : remote.tasks).url(remote.url).state(remote.state)
                .workflow(remote.workflow).update(checked).syncedAt(now).syncProblem(null).build();
    }

    public ProductionChange unverified(ProductionChange stored, String problem) {
        return toBuilder().update(update.waitingFor(unappliedIn(stored))).syncProblem(problem).build();
    }

    public ProductionChange withSyncProblem(String problem) {
        return toBuilder().syncProblem(problem).build();
    }

    public boolean differsFrom(ProductionChange stored) {
        return !toBuilder().syncedAt(stored.syncedAt).syncProblem(stored.syncProblem).build().equals(stored);
    }

    static String shortDescriptionOf(Product product, String fixVersion, List<JiraIssue> epics) {
        String release = product.name() + " " + fixVersion;
        String text = epics.isEmpty() ? release + " production release"
                : release + ": " + epics.stream().map(JiraIssue::summary).collect(joining("; "));
        return abbreviateBytes(text, SHORT_DESCRIPTION_MAX);
    }

    static String descriptionOf(Product product, String departmentName, List<TaskText> tasks, String fixVersion,
                                ChangeSchedule schedule, ChangeTemplate template, List<JiraIssue> epics,
                                List<JiraIssue> stories) {
        String head = "Production release " + fixVersion + " of " + product.name() + " (" + product.code() + ")"
                + (departmentName == null ? "" : " in " + departmentName) + ".\n"
                + schedule.text() + ". "
                + (template.downtime() ? "Downtime expected during the installation." : "No downtime.") + "\n\n"
                + "Change tasks: " + tasks.stream().map(TaskText::shortDescription).collect(joining("; ")) + ".\n\n"
                + "Scope from Jira project " + template.jiraProjectKey() + ", FixVersion " + fixVersion + ":\n";
        String tail = "\n" + detailsOf(product, template);
        List<String> lines = new ArrayList<>();
        for (JiraIssue epic : epics) {
            lines.add(epic.line());
            stories.stream().filter(story -> epic.key().equals(story.epicKey()))
                    .map(story -> "- " + story.line()).forEach(lines::add);
        }
        StringBuilder text = new StringBuilder(head);
        int room = DESCRIPTION_MAX - bytes(head) - bytes(tail) - 60;
        for (int index = 0; index < lines.size(); index++) {
            int size = bytes(lines.get(index)) + 1;
            if (room - size < 0) {
                text.append("...and ").append(lines.size() - index).append(" more issues in Jira.\n");
                break;
            }
            room -= size;
            text.append(lines.get(index)).append('\n');
        }
        return abbreviateBytes(text.append(tail).toString().strip(), DESCRIPTION_MAX);
    }

    private List<ChangeTask> editedTasks(List<ChangeTask> requested, ValidationProblems problems) {
        validateTasks(requested.stream().map(ChangeTask::text).toList(), problems);
        Map<String, ChangeTask> stored = tasks.stream().filter(task -> task.number() != null)
                .collect(toMap(ChangeTask::number, identity(), (first, second) -> first));
        Set<String> listed = new HashSet<>();
        List<ChangeTask> edited = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            ChangeTask task = requested.get(index);
            ChangeTask known = task.number() == null ? null : stored.get(task.number());
            String field = "tasks[" + index + "].number";
            if (task.number() != null && known == null) {
                problems.add(field, "is not a change task of " + number);
            } else if (task.number() != null && !listed.add(task.number())) {
                problems.add(field, "is listed more than once");
            } else if (known != null && known.state() == CANCELED) {
                problems.add(field, "is canceled in ProTech");
            } else if (known != null && known.state() == CLOSED && !known.text().equals(task.text())) {
                problems.add(field, "is closed in ProTech and cannot be changed");
            }
            edited.add(task.in(known == null ? OPEN : known.state()));
        }
        tasks.stream().filter(task -> task.state() == CLOSED && !listed.contains(task.number()))
                .forEach(task -> problems.add("tasks", task.number() + " is closed in ProTech and cannot be removed"));
        tasks.stream().filter(task -> task.state() == CANCELED).forEach(edited::add);
        return edited;
    }

    private List<TaskText> texts() {
        return tasks.stream().filter(task -> task.state() != CANCELED).map(ChangeTask::text).toList();
    }

    private List<ChangeTask> tasksIn(ProductionChange remote) {
        Map<String, TaskState> states = remote.tasks.stream().filter(task -> task.number() != null)
                .collect(toMap(ChangeTask::number, ChangeTask::state, (first, second) -> first));
        return tasks.stream().map(task -> task.in(states.getOrDefault(task.number(), task.state()))).toList();
    }

    private static void differs(List<String> paths, String path, Object mine, Object theirs) {
        if (!Objects.equals(mine, theirs)) {
            paths.add(path);
        }
    }

    private static String detailsOf(Product product, ChangeTemplate template) {
        Planning planning = template.planning();
        List<String> risks = template.riskAssessment().lines().stream()
                .map(line -> abbreviateBytes(line, SECTION_MAX)).toList();
        return section("Test summary", planning.testSummary())
                + section("Implementation plan", planning.implementationPlan())
                + section("Validation plan", planning.validationPlan())
                + section("Backout plan", planning.backoutPlan())
                + section("First use plan", planning.firstUsePlan())
                + abbreviateBytes(template.privilegedAccess().text(), SECTION_MAX) + "\n\n"
                + "Risk assessment:\n" + (risks.isEmpty() ? "Not assessed." : String.join("\n", risks)) + "\n"
                + (template.description() == null ? ""
                : "\n" + section("About " + product.name(), template.description()));
    }

    private static String section(String title, String text) {
        return title + ":\n" + abbreviateBytes(text, SECTION_MAX) + "\n\n";
    }
}

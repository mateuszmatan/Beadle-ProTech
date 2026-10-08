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
import java.util.function.Function;
import java.util.stream.Stream;

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
                               Long departmentId, String departmentName, String openedBy, String fixVersion,
                               ChangeSchedule schedule, String shortDescription, String description,
                               ChangeTemplate template, List<String> epicKeys, List<String> storyKeys,
                               List<ChangeTask> tasks, String url, ChangeState state, List<WorkflowStep> workflow,
                               Instant syncedAt, String syncProblem, ChangeUpdate update, Long version,
                               Long editedVersion, Instant createdAt) {

    public static final int FIX_VERSION_MAX = 100;
    public static final int SHORT_DESCRIPTION_MAX = 160;
    public static final int DESCRIPTION_MAX = 4000;
    public static final Duration APPLY_LIMIT = ofMinutes(1);
    static final int SECTION_MAX = 300;

    private static final List<Edited> EDITED = List.of(
            new Edited("shortDescription", ProductionChange::shortDescription),
            new Edited("description", ProductionChange::description),
            new Edited("schedule.installationStart", change -> change.schedule.installationStart()),
            new Edited("schedule.installationEnd", change -> change.schedule.installationEnd()),
            new Edited("schedule.validationStart", change -> change.schedule.validationStart()),
            new Edited("schedule.validationEnd", change -> change.schedule.validationEnd()),
            new Edited("schedule.firstUsage", change -> change.schedule.firstUsage()),
            new Edited("schedule.downtimeStart", change -> change.schedule.downtimeStart()),
            new Edited("schedule.downtimeEnd", change -> change.schedule.downtimeEnd()),
            new Edited("template.requestedFor", change -> change.template.requestedFor()),
            new Edited("template.requestedBy", change -> change.template.requestedBy()),
            new Edited("template.department", change -> change.template.department()),
            new Edited("template.assignmentGroup", change -> change.template.assignmentGroup()),
            new Edited("template.category", change -> change.template.category()),
            new Edited("template.assignedTo", change -> change.template.assignedTo()),
            new Edited("template.release", change -> change.template.release()),
            new Edited("template.configurationItem", change -> change.template.configurationItem()),
            new Edited("template.incident", change -> change.template.incident()),
            new Edited("template.directBusinessService", change -> change.template.directBusinessService()),
            new Edited("template.problem", change -> change.template.problem()),
            new Edited("template.affectedClients", change -> change.template.affectedClients()),
            new Edited("template.usersAffected", change -> change.template.usersAffected()),
            new Edited("template.description", change -> change.template.description()),
            new Edited("template.approvers", change -> change.template.approvers()),
            new Edited("template.downtime", change -> change.template.downtime()),
            new Edited("template.planning", change -> change.template.planning()),
            new Edited("template.privilegedAccess", change -> change.template.privilegedAccess()),
            new Edited("template.riskAssessment", change -> change.template.riskAssessment()),
            new Edited("template.secureCodingTicket", change -> change.template.secureCodingTicket()),
            new Edited("tasks", ProductionChange::texts));

    public ProductionChange {
        epicKeys = List.copyOf(epicKeys);
        storyKeys = List.copyOf(storyKeys);
        tasks = List.copyOf(tasks);
        state = getIfNull(state, DRAFT);
        workflow = List.copyOf(emptyIfNull(workflow));
        editedVersion = getIfNull(editedVersion, version);
    }

    public static ProductionChange draft(Product product, Long departmentId, String departmentName, String openedBy,
                                         List<TaskText> tasks, String fixVersion, ChangeSchedule schedule,
                                         ChangeTemplate template, List<JiraIssue> epics, List<JiraIssue> stories,
                                         String shortDescription, String description) {
        ChangeTemplate raised = template.releasedAs(fixVersion).openedBy(openedBy, departmentName);
        String summary = isBlank(shortDescription) ? shortDescriptionOf(product, fixVersion, epics)
                : shortDescription.trim();
        String text = isBlank(description)
                ? descriptionOf(product, departmentName, tasks, fixVersion, schedule, raised, epics, stories)
                : description.trim();
        return builder().productId(product.id()).productCode(product.code()).productName(product.name())
                .departmentId(departmentId).departmentName(departmentName).openedBy(openedBy).fixVersion(fixVersion)
                .schedule(schedule).shortDescription(summary).description(text).template(raised)
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

    public static void checkTemplate(ChangeTemplate template, ValidationProblems problems) {
        problems.require("template", template, "fill in the ProTech fields of the change");
        if (template != null) {
            template.validate(problems.at("template"));
        }
    }

    public static void checkTemplateAndSchedule(ChangeTemplate template, ChangeSchedule schedule, Instant upcomingFrom,
                                                ValidationProblems problems) {
        checkTemplate(template, problems);
        problems.require("schedule", schedule, "choose when the change is installed, validated and first used");
        if (schedule != null) {
            schedule.check(template != null && template.downtime(), problems.at("schedule"));
            if (upcomingFrom != null) {
                schedule.checkUpcoming(upcomingFrom, problems.at("schedule"));
            }
        }
    }

    public List<String> unappliedIn(ProductionChange remote) {
        return EDITED.stream().filter(field -> !Objects.equals(field.value().apply(this), field.value().apply(remote)))
                .map(Edited::path).toList();
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
                + schedule.text() + "\n\n"
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
            ChangeTask known = stored.get(task.number());
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

    private static String detailsOf(Product product, ChangeTemplate template) {
        Planning planning = template.planning();
        String risk = Stream.concat(Stream.of("Risk: " + getIfNull(template.risk(), "not assessed")),
                template.riskAssessment().lines().stream()).collect(joining("\n"));
        return Stream.of(section("Test summary", planning.testSummary()),
                        section("Implementation plan", planning.implementationPlan()),
                        section("Validation plan", planning.validationPlan()),
                        section("Backout plan", planning.backoutPlan()),
                        section("First use plan", planning.firstUsePlan()),
                        abbreviateBytes(template.privilegedAccess().text(), SECTION_MAX), risk,
                        section("Users affected", template.usersAffected()),
                        template.secureCodingTicket() == null ? null
                                : "Secure coding ticket: " + template.secureCodingTicket(),
                        section("About " + product.name(), template.description()))
                .filter(Objects::nonNull).collect(joining("\n\n"));
    }

    private static String section(String title, String text) {
        return text == null ? null : title + ":\n" + abbreviateBytes(text, SECTION_MAX);
    }

    private record Edited(String path, Function<ProductionChange, Object> value) {
    }
}

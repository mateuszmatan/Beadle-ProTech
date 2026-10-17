package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeFields;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.TaskFields;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRole;
import com.bbh.itss.beadle.protech.domain.change.ApprovalState;
import com.bbh.itss.beadle.protech.domain.change.ChangeApproval;
import com.bbh.itss.beadle.protech.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Planning;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Type;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RiskAssessment;
import com.bbh.itss.beadle.protech.domain.change.RiskAssessment.Question;
import com.bbh.itss.beadle.protech.domain.change.TaskDetails;
import com.bbh.itss.beadle.protech.domain.change.TaskState;
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.SYS_ID;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.UPDATED;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.dateTime;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.REQUESTED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeSchedule.UNPLANNED;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.Strings.CI;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * How a change and its tasks look in ProTech: the fields Beadle writes to {@code change_request} and
 * {@code change_task}, and the change, its tasks and their approvals Beadle reads back from them. A field ProTech
 * does not answer, because it is not mapped or ProTech has no such field, keeps the value Beadle knows; the risk
 * assessment and the privileged access are written as text and never read back.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ProTechRecords {

    private final ProTechFields fields;

    /** The fields of {@code change_request} Beadle reads. */
    List<String> changeColumns() {
        ChangeFields change = fields.change();
        return columns(Stream.of(SYS_ID, UPDATED, change.number(), change.shortDescription(), change.description(),
                change.installationStart(), change.installationEnd(), change.validationStart(),
                change.validationEnd(), change.firstUsage(), change.downtimeStart(), change.downtimeEnd(),
                change.requestedFor(), change.requestedBy(), change.department(), change.assignmentGroup(),
                change.category(), change.assignedTo(), change.type(), change.release(), change.configurationItem(),
                change.incident(), change.directBusinessService(), change.problem(), change.affectedClients(),
                change.usersAffected(), change.l1Manager(), change.l2Manager(), change.businessApprover(),
                change.supportApprover(), change.downtime(), change.testSummary(), change.implementationPlan(),
                change.validationPlan(), change.backoutPlan(), change.firstUsePlan(), change.secureCodingTicket(),
                change.state()));
    }

    /** The fields of {@code change_task} Beadle reads. */
    List<String> taskColumns() {
        TaskFields task = fields.task();
        return columns(Stream.of(SYS_ID, UPDATED, task.number(), task.change(), task.assignmentGroup(),
                task.assignedTo(), task.configurationItem(), task.platform(), task.application(), task.packages(),
                task.backoutPackages(), task.importance(), task.shortDescription(), task.description(),
                task.additionalComments(), task.start(), task.state(), task.approval()));
    }

    /** The fields of {@code sysapproval_approver} Beadle reads. */
    List<String> approvalColumns() {
        return columns(Stream.of(SYS_ID, UPDATED, fields.approval().approving(), fields.approval().approver(),
                fields.approval().state()));
    }

    /** The fields of {@code change_request} for a change, as display values; an empty value clears the field. */
    Map<String, Object> changeBody(ProductionChange change) {
        ChangeFields columns = fields.change();
        ChangeTemplate template = getIfNull(change.template(), () -> ChangeTemplate.builder().build());
        ChangeSchedule schedule = getIfNull(change.schedule(), UNPLANNED);
        Approvers approvers = template.approvers();
        Planning planning = getIfNull(template.planning(), () -> new Planning(null, null, null, null, null));
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, columns.shortDescription(), change.shortDescription());
        put(body, columns.description(), change.description());
        put(body, columns.installationStart(), dateTime(schedule.installationStart()));
        put(body, columns.installationEnd(), dateTime(schedule.installationEnd()));
        put(body, columns.validationStart(), dateTime(schedule.validationStart()));
        put(body, columns.validationEnd(), dateTime(schedule.validationEnd()));
        put(body, columns.firstUsage(), dateTime(schedule.firstUsage()));
        put(body, columns.downtimeStart(), dateTime(schedule.downtimeStart()));
        put(body, columns.downtimeEnd(), dateTime(schedule.downtimeEnd()));
        put(body, columns.requestedFor(), template.requestedFor());
        put(body, columns.requestedBy(), template.requestedBy());
        put(body, columns.department(), template.department());
        put(body, columns.assignmentGroup(), template.assignmentGroup());
        put(body, columns.category(), template.category());
        put(body, columns.assignedTo(), template.assignedTo());
        put(body, columns.type(), template.type() == null ? null : template.type().label());
        put(body, columns.release(), template.release());
        put(body, columns.configurationItem(), template.configurationItem());
        put(body, columns.incident(), template.incident());
        put(body, columns.directBusinessService(), template.directBusinessService());
        put(body, columns.problem(), template.problem());
        put(body, columns.risk(), template.risk());
        put(body, columns.affectedClients(), template.affectedClients());
        put(body, columns.usersAffected(), template.usersAffected());
        put(body, columns.l1Manager(), approvers.l1Manager());
        put(body, columns.l2Manager(), approvers.l2Manager());
        put(body, columns.businessApprover(), approvers.businessApprover());
        put(body, columns.supportApprover(), approvers.supportApprover());
        put(body, columns.downtime(), String.valueOf(template.downtime()));
        put(body, columns.testSummary(), planning.testSummary());
        put(body, columns.implementationPlan(), planning.implementationPlan());
        put(body, columns.validationPlan(), planning.validationPlan());
        put(body, columns.backoutPlan(), planning.backoutPlan());
        put(body, columns.firstUsePlan(), planning.firstUsePlan());
        put(body, columns.privilegedAccess(), textOf(template.privilegedAccess()));
        put(body, columns.riskAssessment(), textOf(template.riskAssessment()));
        put(body, columns.secureCodingTicket(), template.secureCodingTicket());
        return body;
    }

    /** The fields of {@code change_task} for a task of the change numbered {@code changeNumber}. */
    Map<String, Object> taskBody(String changeNumber, ChangeTask task) {
        TaskFields columns = fields.task();
        TaskDetails details = task.details();
        Map<String, Object> body = new LinkedHashMap<>();
        if (changeNumber != null) {
            put(body, columns.change(), changeNumber);
        }
        put(body, columns.assignmentGroup(), details.assignmentGroup());
        put(body, columns.assignedTo(), details.assignedTo());
        put(body, columns.configurationItem(), details.configurationItem());
        put(body, columns.platform(), details.platform());
        put(body, columns.application(), details.application());
        put(body, columns.packages(), details.packages());
        put(body, columns.backoutPackages(), details.backoutPackages());
        put(body, columns.importance(), details.importance());
        put(body, columns.shortDescription(), details.shortDescription());
        put(body, columns.description(), details.description());
        put(body, columns.additionalComments(), details.additionalComments());
        put(body, columns.start(), dateTime(task.start()));
        return body;
    }

    /** The value of {@code change_task.state} that cancels a task: the first one listed for CANCELED. */
    Map<String, Object> canceled() {
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, fields.task().state(), fields.taskStates().get(TaskState.CANCELED).get(0));
        return body;
    }

    /**
     * The change as ProTech holds it: its texts, schedule and fields from {@code change}, its state, a workflow step
     * when the state moved since Beadle saw it, its tasks with their approvals, and the approvals of its approvers.
     */
    ProductionChange changeOf(ProductionChange known, TableRow change, List<TableRow> tasks,
                              Map<String, List<TableRow>> approvals, String url, Instant now) {
        ChangeFields columns = fields.change();
        ChangeTemplate template = templateOf(getIfNull(known.template(), () -> ChangeTemplate.builder().build()),
                change);
        ChangeState state = changeStateOf(change, known.state());
        return known.toBuilder()
                .shortDescription(change.text(columns.shortDescription(), known.shortDescription()))
                .description(change.text(columns.description(), known.description()))
                .schedule(scheduleOf(getIfNull(known.schedule(), UNPLANNED), change))
                .template(template)
                .tasks(tasksOf(emptyIfNull(known.tasks()), tasks, approvals))
                .url(url)
                .state(state)
                .workflow(workflowOf(emptyIfNull(known.workflow()), state, change.instant(UPDATED, now)))
                .approvals(approvalsOf(template.approvers(), state,
                        approvals.getOrDefault(change.sysId(), List.of())))
                .build();
    }

    /** The state of {@code change}, or {@code fallback} when ProTech answers a value no state lists. */
    ChangeState changeStateOf(TableRow change, ChangeState fallback) {
        return stateOf(fields.changeStates(), change, fields.change().state(), fallback);
    }

    TaskState taskStateOf(TableRow task, TaskState fallback) {
        return stateOf(fields.taskStates(), task, fields.task().state(), fallback);
    }

    ApprovalState approvalOf(TableRow row, String column) {
        return stateOf(fields.approvalStates(), row, column, NOT_APPROVED);
    }

    /** The values of {@code sysapproval_approver.state} that wait for the approver. */
    List<String> requested() {
        return fields.approvalStates().get(REQUESTED);
    }

    private ChangeTemplate templateOf(ChangeTemplate known, TableRow change) {
        ChangeFields columns = fields.change();
        Approvers approvers = known.approvers();
        Planning planning = known.planning();
        boolean planned = planning != null || Stream.of(columns.testSummary(), columns.implementationPlan(),
                columns.validationPlan(), columns.backoutPlan(), columns.firstUsePlan()).anyMatch(change::has);
        Planning base = getIfNull(planning, () -> new Planning(null, null, null, null, null));
        return known.toBuilder()
                .requestedFor(change.text(columns.requestedFor(), known.requestedFor()))
                .requestedBy(change.text(columns.requestedBy(), known.requestedBy()))
                .department(change.text(columns.department(), known.department()))
                .assignmentGroup(change.text(columns.assignmentGroup(), known.assignmentGroup()))
                .category(change.text(columns.category(), known.category()))
                .assignedTo(change.text(columns.assignedTo(), known.assignedTo()))
                .type(typeOf(change.text(columns.type(), null), known.type()))
                .release(change.text(columns.release(), known.release()))
                .configurationItem(change.text(columns.configurationItem(), known.configurationItem()))
                .incident(change.text(columns.incident(), known.incident()))
                .directBusinessService(change.text(columns.directBusinessService(), known.directBusinessService()))
                .problem(change.text(columns.problem(), known.problem()))
                .affectedClients(change.text(columns.affectedClients(), known.affectedClients()))
                .usersAffected(change.text(columns.usersAffected(), known.usersAffected()))
                .approvers(new Approvers(change.text(columns.l1Manager(), approvers.l1Manager()),
                        change.text(columns.l2Manager(), approvers.l2Manager()),
                        change.text(columns.businessApprover(), approvers.businessApprover()),
                        change.text(columns.supportApprover(), approvers.supportApprover())))
                .downtime(change.has(columns.downtime()) ? truthy(change.value(columns.downtime()))
                        : known.downtime())
                .planning(!planned ? null : new Planning(change.text(columns.testSummary(), base.testSummary()),
                        change.text(columns.implementationPlan(), base.implementationPlan()),
                        change.text(columns.validationPlan(), base.validationPlan()),
                        change.text(columns.backoutPlan(), base.backoutPlan()),
                        change.text(columns.firstUsePlan(), base.firstUsePlan())))
                .secureCodingTicket(change.text(columns.secureCodingTicket(), known.secureCodingTicket()))
                .build();
    }

    private ChangeSchedule scheduleOf(ChangeSchedule known, TableRow change) {
        ChangeFields columns = fields.change();
        return new ChangeSchedule(change.instant(columns.installationStart(), known.installationStart()),
                change.instant(columns.installationEnd(), known.installationEnd()),
                change.instant(columns.validationStart(), known.validationStart()),
                change.instant(columns.validationEnd(), known.validationEnd()),
                change.instant(columns.firstUsage(), known.firstUsage()),
                change.instant(columns.downtimeStart(), known.downtimeStart()),
                change.instant(columns.downtimeEnd(), known.downtimeEnd()));
    }

    private List<ChangeTask> tasksOf(List<ChangeTask> known, List<TableRow> rows,
                                     Map<String, List<TableRow>> approvals) {
        TaskFields columns = fields.task();
        Map<String, ChangeTask> byNumber = known.stream().filter(task -> task.number() != null)
                .collect(toMap(ChangeTask::number, identity(), (first, second) -> first, LinkedHashMap::new));
        List<String> order = List.copyOf(byNumber.keySet());
        Comparator<TableRow> listed = comparing(row -> order.contains(row.value(columns.number()))
                ? order.indexOf(row.value(columns.number())) : order.size());
        return rows.stream().sorted(listed.thenComparing(row -> row.value(columns.number()),
                        nullsLast(Comparator.<String>naturalOrder())))
                .map(row -> taskOf(byNumber.get(row.value(columns.number())), row,
                        approvals.getOrDefault(row.sysId(), List.of())))
                .toList();
    }

    private ChangeTask taskOf(ChangeTask known, TableRow row, List<TableRow> approvals) {
        TaskFields columns = fields.task();
        TaskDetails details = known == null ? TaskDetails.builder().build() : known.details();
        TaskDetails read = details.toBuilder()
                .assignmentGroup(row.text(columns.assignmentGroup(), details.assignmentGroup()))
                .assignedTo(row.text(columns.assignedTo(), details.assignedTo()))
                .configurationItem(row.text(columns.configurationItem(), details.configurationItem()))
                .platform(row.text(columns.platform(), details.platform()))
                .application(row.text(columns.application(), details.application()))
                .packages(row.text(columns.packages(), details.packages()))
                .backoutPackages(row.text(columns.backoutPackages(), details.backoutPackages()))
                .importance(row.text(columns.importance(), details.importance()))
                .shortDescription(row.text(columns.shortDescription(), details.shortDescription()))
                .description(row.text(columns.description(), details.description()))
                .additionalComments(row.text(columns.additionalComments(), details.additionalComments()))
                .build();
        ApprovalState approval = approvals.isEmpty() ? approvalOf(row, columns.approval())
                : combined(approvals.stream().map(found -> approvalOf(found, fields.approval().state())).toList());
        List<String> approvers = approvers(approvals);
        return new ChangeTask(row.value(columns.number()), read,
                row.instant(columns.start(), known == null ? null : known.start()), approval,
                approvers.isEmpty() && known != null ? known.approvers() : approvers,
                known == null ? null : known.reminder(),
                taskStateOf(row, known == null ? TaskState.OPEN : known.state()));
    }

    private List<ChangeApproval> approvalsOf(Approvers named, ChangeState state, List<TableRow> rows) {
        String approverColumn = fields.approval().approver();
        return Stream.of(ApprovalRole.values()).map(role -> {
            String approver = role.approverIn(named);
            List<ApprovalState> found = approver == null ? List.of() : rows.stream()
                    .filter(row -> CI.equals(row.display(approverColumn), approver))
                    .map(row -> approvalOf(row, fields.approval().state())).toList();
            return new ChangeApproval(role, approver, found.isEmpty() ? reachedBy(state, role.stage())
                    : combined(found), null);
        }).toList();
    }

    List<String> approvers(Collection<TableRow> approvals) {
        Set<String> names = new LinkedHashSet<>();
        approvals.stream().map(row -> row.display(fields.approval().approver()))
                .filter(name -> name != null && !name.isBlank()).forEach(names::add);
        return List.copyOf(names);
    }

    private static List<WorkflowStep> workflowOf(List<WorkflowStep> known, ChangeState state, Instant at) {
        if (!known.isEmpty() && known.get(known.size() - 1).state() == state) {
            return known;
        }
        List<WorkflowStep> moved = new ArrayList<>(known);
        moved.add(new WorkflowStep(state, at));
        return moved;
    }

    private static ApprovalState combined(List<ApprovalState> states) {
        return states.contains(APPROVED) ? APPROVED : states.contains(REQUESTED) ? REQUESTED : NOT_APPROVED;
    }

    private static ApprovalState reachedBy(ChangeState state, ChangeState stage) {
        int reached = state.compareTo(stage);
        return reached < 0 ? NOT_APPROVED : reached == 0 ? REQUESTED : APPROVED;
    }

    private static <S extends Enum<S>> S stateOf(Map<S, List<String>> states, TableRow row, String column,
                                                 S fallback) {
        if (!row.has(column)) {
            return fallback;
        }
        String value = row.value(column);
        String display = row.display(column);
        return states.entrySet().stream()
                .filter(state -> state.getValue().stream().anyMatch(listed -> CI.equals(listed, value)
                        || CI.equals(listed, display)))
                .map(Map.Entry::getKey).findFirst().orElse(fallback);
    }

    private static Type typeOf(String label, Type fallback) {
        return label == null ? fallback : Stream.of(Type.values()).filter(type -> CI.equals(type.label(), label)
                || CI.equals(type.name(), label)).findFirst().orElse(fallback);
    }

    private static boolean truthy(String value) {
        return CI.equalsAny(value, "true", "1", "yes");
    }

    private static String textOf(PrivilegedAccess access) {
        return !access.required() ? "Privileged access: not needed." : "Privileged access needed for: "
                + access.users().stream().map(user -> user.user() + " (" + user.account() + ")")
                .collect(joining(", ")) + ".";
    }

    private static String textOf(RiskAssessment assessment) {
        return Stream.of(Question.values()).map(question -> question.label() + ": " + question.answerIn(assessment))
                .collect(joining("\n"));
    }

    private static void put(Map<String, Object> body, String column, String value) {
        if (!column.isEmpty()) {
            body.put(column, value == null ? "" : value);
        }
    }

    private static List<String> columns(Stream<String> columns) {
        return columns.filter(column -> !column.isEmpty()).distinct().toList();
    }
}

package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ApprovalField;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Field;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.TaskField;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRole;
import com.bbh.itss.beadle.protech.domain.change.ApprovalState;
import com.bbh.itss.beadle.protech.domain.change.ChangeApproval;
import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Type;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.TaskState;
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ApprovalField.APPROVER;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.SYS_ID;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.UPDATED;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.dateTime;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.instantOf;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.REQUESTED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeSchedule.UNPLANNED;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.Strings.CI;

/**
 * How a change and its tasks look in ProTech, driven by the fields of {@link ProTechFields}: a record of Beadle is
 * turned into its JSON, each field is taken from (or put at) its path there, and the JSON is turned back into the
 * record. A field ProTech does not answer, because it is not mapped or ProTech has no such field, keeps the value
 * Beadle knows; the written-only fields, such as the risk assessment, are never read back.
 */
@RequiredArgsConstructor
class ProTechRecords {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final ChangeTemplate NO_TEMPLATE = ChangeTemplate.builder().build();

    private final ProTechFields fields;

    /** The fields of a table Beadle asks for when it reads it. */
    List<String> columns(Field[] table) {
        return columns(Stream.concat(Stream.of(SYS_ID, UPDATED), Stream.of(table)
                .filter(field -> field.conversion().listed()).map(fields::column)));
    }

    /** The fields of {@code change_request} for a change, as display values; an empty value clears the field. */
    Map<String, Object> changeBody(ProductionChange change) {
        ChangeTemplate template = getIfNull(change.template(), NO_TEMPLATE);
        ObjectNode json = JSON.valueToTree(change.toBuilder().template(template).build());
        json.withObject("/template").put("privilegedAccess", template.privilegedAccess().text())
                .put("riskAssessment", template.riskAssessment().text());
        return body(json, ChangeField.values());
    }

    /** The fields of {@code change_task} for a task, with the change it belongs to unless {@code changeNumber} is null. */
    Map<String, Object> taskBody(String changeNumber, ChangeTask task) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (changeNumber != null) {
            put(body, fields.column(TaskField.CHANGE), changeNumber);
        }
        body.putAll(body(JSON.valueToTree(task), TaskField.values()));
        return body;
    }

    /** The value of {@code change_task.state} that cancels a task: the first one listed for CANCELED. */
    Map<String, Object> canceled() {
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, fields.column(TaskField.STATE), fields.taskStates().get(TaskState.CANCELED).getFirst());
        return body;
    }

    /**
     * The change as ProTech holds it: its fields from {@code change}, its state, a workflow step when the state moved
     * since Beadle saw it, its tasks with their approvals, and the approvals of its approvers.
     */
    ProductionChange changeOf(ProductionChange known, TableRow change, List<TableRow> tasks,
                              Map<String, List<TableRow>> approvals, String url, Instant now) {
        ProductionChange read = read(known.toBuilder().template(getIfNull(known.template(), NO_TEMPLATE))
                .schedule(getIfNull(known.schedule(), UNPLANNED)).build(), change, ChangeField.values());
        ChangeState state = changeStateOf(change, known.state());
        return read.toBuilder()
                .tasks(tasksOf(known.tasks(), tasks, approvals))
                .url(url)
                .state(state)
                .workflow(workflowOf(known.workflow(), state, change.instant(UPDATED, now)))
                .approvals(approvalsOf(read.template().approvers(), state,
                        approvals.getOrDefault(change.sysId(), List.of())))
                .build();
    }

    /** The state of {@code change}, or {@code fallback} when ProTech answers a value no state lists. */
    ChangeState changeStateOf(TableRow change, ChangeState fallback) {
        return stateOf(fields.changeStates(), change, fields.column(ChangeField.STATE), fallback);
    }

    TaskState taskStateOf(TableRow task, TaskState fallback) {
        return stateOf(fields.taskStates(), task, fields.column(TaskField.STATE), fallback);
    }

    /** The values of {@code sysapproval_approver.state} that wait for the approver. */
    List<String> requested() {
        return fields.approvalStates().get(REQUESTED);
    }

    /** The approvers named on approvals, each once. */
    List<String> approvers(Collection<TableRow> approvals) {
        return approvals.stream().map(row -> row.display(fields.column(APPROVER)))
                .filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

    private Map<String, Object> body(JsonNode json, Field[] table) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Field field : table) {
            if (field.path() != null) {
                put(body, fields.column(field), written(field, json.at(field.pointer())));
            }
        }
        return body;
    }

    /** The record with every field of {@code table} that {@code row} answers read into it. */
    @SuppressWarnings("unchecked")
    private <T> T read(T known, TableRow row, Field[] table) {
        ObjectNode json = JSON.valueToTree(known);
        for (Field field : table) {
            String column = fields.column(field);
            if (field.conversion().read() && row.has(column)) {
                JsonPointer pointer = field.pointer();
                ObjectNode parent = pointer.head().matches() ? json : json.withObject(pointer.head());
                String name = pointer.last().getMatchingProperty();
                parent.set(name, readValue(field, row, column, parent.get(name)));
            }
        }
        return (T) JSON.treeToValue(json, known.getClass());
    }

    private List<ChangeTask> tasksOf(List<ChangeTask> known, List<TableRow> rows,
                                     Map<String, List<TableRow>> approvals) {
        String number = fields.column(TaskField.NUMBER);
        Map<String, ChangeTask> byNumber = known.stream().filter(task -> task.number() != null)
                .collect(toMap(ChangeTask::number, identity(), (first, second) -> first, LinkedHashMap::new));
        List<String> order = List.copyOf(byNumber.keySet());
        Function<TableRow, Integer> listed = row -> order.contains(row.value(number))
                ? order.indexOf(row.value(number)) : order.size();
        return rows.stream().sorted(comparing(listed).thenComparing(row -> row.value(number),
                        nullsLast(Comparator.<String>naturalOrder())))
                .map(row -> taskOf(byNumber.get(row.value(number)), row,
                        approvals.getOrDefault(row.sysId(), List.of())))
                .toList();
    }

    private ChangeTask taskOf(ChangeTask known, TableRow row, List<TableRow> approvals) {
        ChangeTask read = read(getIfNull(known, () -> new ChangeTask(null, null, null, null, null, null, null)), row,
                TaskField.values());
        List<String> approvers = approvers(approvals);
        return read.withNumber(row.value(fields.column(TaskField.NUMBER)))
                .withApproval(approvals.isEmpty() ? approvalOf(row, fields.column(TaskField.APPROVAL))
                        : combined(approvals))
                .withApprovers(approvers.isEmpty() ? read.approvers() : approvers)
                .withState(taskStateOf(row, read.state()));
    }

    private List<ChangeApproval> approvalsOf(Approvers named, ChangeState state, List<TableRow> rows) {
        return Stream.of(ApprovalRole.values()).map(role -> {
            String approver = role.approverIn(named);
            List<TableRow> found = approver == null ? List.of() : rows.stream()
                    .filter(row -> CI.equals(row.display(fields.column(APPROVER)), approver)).toList();
            return new ChangeApproval(role, approver, found.isEmpty() ? role.approvalIn(state) : combined(found),
                    null);
        }).toList();
    }

    private ApprovalState combined(List<TableRow> approvals) {
        List<ApprovalState> states = approvals.stream()
                .map(row -> approvalOf(row, fields.column(ApprovalField.STATE))).toList();
        return states.contains(APPROVED) ? APPROVED : states.contains(REQUESTED) ? REQUESTED : NOT_APPROVED;
    }

    private ApprovalState approvalOf(TableRow row, String column) {
        return stateOf(fields.approvalStates(), row, column, NOT_APPROVED);
    }

    private static List<WorkflowStep> workflowOf(List<WorkflowStep> known, ChangeState state, Instant at) {
        return !known.isEmpty() && known.getLast().state() == state ? known
                : Stream.concat(known.stream(), Stream.of(new WorkflowStep(state, at))).toList();
    }

    /** The value ProTech is given for a field, from the JSON of Beadle's record. */
    private static String written(Field field, JsonNode node) {
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        return switch (field.conversion()) {
            case DATE_TIME -> dateTime(Instant.parse(node.asString()));
            case TYPE -> Type.valueOf(node.asString()).label();
            default -> node.asString();
        };
    }

    /** The JSON of the value ProTech answers for a field; an answer no type matches keeps {@code known}. */
    private static JsonNode readValue(Field field, TableRow row, String column, JsonNode known) {
        return switch (field.conversion()) {
            case DATE_TIME -> nodeOf(instantOf(row.value(column)));
            case FLAG -> BooleanNode.valueOf(CI.equalsAny(row.value(column), "true", "1", "yes"));
            case TYPE -> Stream.of(Type.values())
                    .filter(type -> CI.equalsAny(row.display(column), type.label(), type.name()))
                    .<JsonNode>map(type -> StringNode.valueOf(type.name())).findFirst().orElse(known);
            default -> nodeOf(row.display(column));
        };
    }

    private static JsonNode nodeOf(Object value) {
        return value == null ? NullNode.getInstance() : StringNode.valueOf(value.toString());
    }

    private static <S extends Enum<S>> S stateOf(Map<S, List<String>> states, TableRow row, String column,
                                                 S fallback) {
        if (!row.has(column)) {
            return fallback;
        }
        return states.entrySet().stream()
                .filter(state -> state.getValue().stream()
                        .anyMatch(listed -> CI.equalsAny(listed, row.value(column), row.display(column))))
                .map(Map.Entry::getKey).findFirst().orElse(fallback);
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

package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ApprovalField;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.LookupTable;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.TaskField;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRole;
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.ProTechMode;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;
import com.bbh.itss.beadle.protech.domain.change.TaskState;
import com.bbh.itss.beadle.protech.domain.shared.RefusedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField.BUSINESS_APPROVER;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField.L1_MANAGER;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField.L2_MANAGER;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField.SUPPORT_APPROVER;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.SYS_ID;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.UPDATED;
import static com.bbh.itss.beadle.protech.adapter.out.protech.TableRow.dateTime;
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.MAX_TASKS;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.apache.commons.lang3.ObjectUtils.max;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CI;

/**
 * The live ProTech, used when {@code beadle.protech.instance-url} names it. ProTech runs on ServiceNow; this adapter
 * calls its Table API as the Beadle service account. A change is a {@code change_request}, its tasks are
 * {@code change_task} records that reference it, and the approvals of both are {@code sysapproval_approver} records;
 * the lookups search the tables of {@code beadle.protech.fields.lookups}. Which field of ProTech holds which field of
 * Beadle, and which state values stand for which state, is {@link ProTechFields} (docs/PROTECH-MAPPING.md).
 */
@Component
@ConditionalOnExpression(ProTechProperties.CONFIGURED)
class LiveProTechAdapter implements ProTechPort, ProTechLookupPort {

    static final int APPROVALS_PER_RECORD = 20;
    static final String NO_NUMBER = "ProTech answered without the number of the new record";
    /**
     * How many numbers or sys_ids one {@code IN} query lists, so that its URL stays well within the length of a request
     * line: 50 sys_ids of 32 characters take less than 2 KB, encoded. Longer lists are asked in batches of this size.
     */
    static final int IN_BATCH = 50;

    private final TableApi api;
    private final ProTechRecords records;
    private final ProTechFields fields;
    private final String url;
    private final Clock clock;

    LiveProTechAdapter(ProTechProperties properties, RestClient.Builder builder, Clock clock) {
        this.api = new TableApi(builder, properties);
        this.fields = properties.fields();
        this.records = new ProTechRecords(fields);
        this.url = properties.instanceUrl();
        this.clock = clock;
    }

    @Override
    public ProTechMode mode() {
        return ProTechMode.LIVE;
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        String number = numberOf(api.create(column(ChangeField.TABLE), records.changeBody(change),
                List.of(SYS_ID, column(ChangeField.NUMBER)), "raise the change"), column(ChangeField.NUMBER));
        return new RaisedChange(number, linkTo(number));
    }

    @Override
    public String createTask(String changeNumber, ChangeTask task) {
        return insertTask(heldNumber(changeRow(changeNumber, List.of(SYS_ID)), changeNumber), task);
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        String number = column(ChangeField.NUMBER);
        Map<String, TableRow> changes = byValue(listIn(column(ChangeField.TABLE), number,
                numbers(known.stream().map(ProductionChange::number)), "",
                records.columns(ChangeField.values()), 1, "read the changes"), number);
        if (changes.isEmpty()) {
            return Map.of();
        }
        List<String> changeIds = changes.values().stream().map(TableRow::sysId).toList();
        Map<String, List<TableRow>> tasks = tasksOf(changeIds);
        Map<String, List<TableRow>> approvals = approvalsOf(Stream.concat(changeIds.stream(),
                tasks.values().stream().flatMap(List::stream).map(TableRow::sysId)).filter(Objects::nonNull).toList());
        Map<String, ProductionChange> read = new LinkedHashMap<>();
        for (ProductionChange change : known) {
            TableRow row = changes.get(safe(change.number()));
            if (row != null) {
                read.put(change.number(), records.changeOf(change, row, tasks.getOrDefault(row.sysId(), List.of()),
                        approvals, linkTo(row.value(number)), now(clock)));
            }
        }
        return read;
    }

    @Override
    public void update(ProductionChange change) {
        TableRow row = changeRow(change.number(), List.of(SYS_ID));
        String number = heldNumber(row, change.number());
        api.update(column(ChangeField.TABLE), row.sysId(), records.changeBody(change), true, "update " + number);
        Map<String, TableRow> held = byValue(tasksOf(List.of(row.sysId())).getOrDefault(row.sysId(), List.of()),
                column(TaskField.NUMBER));
        for (ChangeTask task : change.tasks()) {
            TableRow found = task.number() == null ? null : held.remove(task.number());
            if (found == null) {
                insertTask(number, task);
            } else if (open(found)) {
                api.update(column(TaskField.TABLE), found.sysId(), records.taskBody(null, task), true,
                        "update " + task.number() + " of " + number);
            }
        }
        held.forEach((task, dropped) -> {
            if (open(dropped)) {
                api.update(column(TaskField.TABLE), dropped.sysId(), records.canceled(), false,
                        "cancel " + task + " of " + number);
            }
        });
    }

    @Override
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        List<String> approverColumns = Stream.of(L1_MANAGER, L2_MANAGER, BUSINESS_APPROVER, SUPPORT_APPROVER)
                .map(this::column).filter(column -> !column.isEmpty()).toList();
        TableRow change = changeRow(changeNumber, Stream.concat(Stream.of(SYS_ID), approverColumns.stream()).toList());
        String number = heldNumber(change, changeNumber);
        List<TableRow> waiting;
        String reminder;
        if (approval.role() != null) {
            String approver = change.display(column(approverOf(approval.role())));
            waiting = approver == null ? List.of() : requestedOf(change.sysId(), number).stream()
                    .filter(row -> CI.equals(row.display(column(ApprovalField.APPROVER)), approver)).toList();
            reminder = "Beadle reminds you that " + number + " waits for your approval as its "
                    + approval.role().label() + ".";
        } else {
            waiting = requestedOf(taskRow(change.sysId(), number, approval.task()).sysId(), approval.task());
            reminder = "Beadle reminds you that the change task " + approval.task() + " of " + number
                    + " waits for your approval.";
        }
        if (waiting.isEmpty()) {
            throw RefusedException.nobodyToRemind(number);
        }
        for (TableRow row : waiting) {
            api.update(column(ApprovalField.TABLE), row.sysId(), Map.of(column(ApprovalField.COMMENTS), reminder),
                    true, "remind " + row.display(column(ApprovalField.APPROVER)) + " on " + number);
        }
        return records.approvers(waiting);
    }

    @Override
    public List<Lookup> find(LookupKind kind, String query, int limit) {
        LookupTable table = fields.lookups().get(kind);
        StringJoiner encoded = new StringJoiner("^");
        if (!table.filter().isEmpty()) {
            encoded.add(table.filter());
        }
        String text = trimToEmpty(query).replace("^", "^^").replaceAll("[\\r\\n]+", " ");
        if (!text.isEmpty()) {
            encoded.add(table.value() + "LIKE" + text + (table.detail().isEmpty() ? ""
                    : "^OR" + table.detail() + "LIKE" + text));
        }
        encoded.add("ORDERBY" + table.value());
        List<String> columns = Stream.of(table.value(), table.detail()).filter(column -> !column.isEmpty())
                .distinct().toList();
        return api.search(table.table(), encoded.toString(), columns, limit, "look up the " + kind.path())
                .stream().filter(row -> isNotBlank(row.display(table.value())))
                .map(row -> new Lookup(row.display(table.value()),
                        table.detail().isEmpty() ? null : row.display(table.detail())))
                .limit(limit).toList();
    }

    /**
     * The changes, change tasks and approvals ProTech updated after {@code since}, by change number: the state of a
     * change updated since, and the last update of its approvals and of its tasks. The tasks of the changes are all
     * read, to tell which change the approval of a task belongs to.
     */
    @Override
    public Map<String, ChangeActivity> activity(Collection<String> watched, Instant since) {
        List<String> numbers = numbers(watched.stream());
        String changeNumber = column(ChangeField.NUMBER);
        String taskNumber = column(TaskField.NUMBER);
        String taskChange = column(TaskField.CHANGE);
        String updatedSince = since == null ? "" : "^" + UPDATED + ">" + dateTime(since);
        Map<String, String> changeOfTask = new HashMap<>();
        Map<String, Instant> tasksAt = new HashMap<>();
        for (TableRow row : listIn(column(TaskField.TABLE), taskChange + "." + changeNumber, numbers, "",
                List.of(SYS_ID, UPDATED, taskNumber, taskChange), MAX_TASKS * 2, "poll the change tasks")) {
            String number = row.display(taskChange);
            Instant updated = row.instant(UPDATED, null);
            if (number != null && row.value(taskNumber) != null) {
                changeOfTask.put(row.value(taskNumber), number);
            }
            if (number != null && updated != null && (since == null || updated.isAfter(since))) {
                tasksAt.merge(number, updated, (first, second) -> max(first, second));
            }
        }
        Map<String, TableRow> changes = byValue(listIn(column(ChangeField.TABLE), changeNumber, numbers,
                updatedSince, List.of(SYS_ID, UPDATED, changeNumber, column(ChangeField.STATE)), 1,
                "poll the changes"), changeNumber);
        String approving = column(ApprovalField.APPROVING);
        Map<String, Instant> approvalsAt = new HashMap<>();
        for (TableRow row : listIn(column(ApprovalField.TABLE), approving + "." + changeNumber,
                Stream.concat(numbers.stream(), changeOfTask.keySet().stream().sorted()).toList(), updatedSince,
                List.of(SYS_ID, UPDATED, approving), APPROVALS_PER_RECORD, "poll the approvals")) {
            String approved = row.display(approving);
            String number = numbers.contains(approved) ? approved : changeOfTask.get(approved);
            Instant updated = row.instant(UPDATED, null);
            if (number != null && updated != null) {
                approvalsAt.merge(number, updated, (first, second) -> max(first, second));
            }
        }
        Map<String, ChangeActivity> moved = new LinkedHashMap<>();
        for (String number : numbers) {
            TableRow row = changes.get(number);
            if (row != null || approvalsAt.containsKey(number) || tasksAt.containsKey(number)) {
                moved.put(number, new ChangeActivity(number, row == null ? null : records.changeStateOf(row, null),
                        row == null ? null : row.instant(UPDATED, null), approvalsAt.get(number),
                        tasksAt.get(number)));
            }
        }
        return moved;
    }

    /**
     * The records of {@code table} whose {@code column} is one of {@code values}, asked {@value #IN_BATCH} values at a
     * time, each batch for at most {@code perValue} records a value and narrowed by the encoded query {@code more}.
     */
    private List<TableRow> listIn(String table, String column, List<String> values, String more,
                                  List<String> columns, int perValue, String operation) {
        return partition(values, IN_BATCH).stream()
                .flatMap(batch -> api.list(table, column + "IN" + String.join(",", batch) + more, columns,
                        perValue * batch.size(), operation).stream())
                .toList();
    }

    private String insertTask(String changeNumber, ChangeTask task) {
        return numberOf(api.create(column(TaskField.TABLE), records.taskBody(changeNumber, task),
                List.of(SYS_ID, column(TaskField.NUMBER)), "create a change task on " + changeNumber),
                column(TaskField.NUMBER));
    }

    private TableRow changeRow(String changeNumber, List<String> columns) {
        String number = safe(changeNumber);
        List<String> wanted = Stream.concat(columns.stream(), Stream.of(column(ChangeField.NUMBER))).distinct()
                .toList();
        return (number == null ? Stream.<TableRow>empty() : api.list(column(ChangeField.TABLE),
                column(ChangeField.NUMBER) + "=" + number, wanted, 1, "find " + number).stream()).findFirst()
                .orElseThrow(() -> RefusedException.noChange(changeNumber));
    }

    private TableRow taskRow(String changeId, String changeNumber, String taskNumber) {
        String number = safe(taskNumber);
        return (number == null ? Stream.<TableRow>empty() : api.list(column(TaskField.TABLE),
                column(TaskField.CHANGE) + "=" + changeId + "^" + column(TaskField.NUMBER) + "=" + number,
                List.of(SYS_ID, column(TaskField.NUMBER)), 1, "find " + number).stream()).findFirst()
                .orElseThrow(() -> RefusedException.noTask(taskNumber, changeNumber));
    }

    /** The tasks of the changes by the sys_id of their change. */
    private Map<String, List<TableRow>> tasksOf(List<String> changeIds) {
        String change = column(TaskField.CHANGE);
        return groupedBy(listIn(column(TaskField.TABLE), change, changeIds, "^ORDERBY" + column(TaskField.NUMBER),
                records.columns(TaskField.values()), MAX_TASKS * 2, "read the change tasks"), change);
    }

    /** The approvals of the changes and tasks by the sys_id they approve. */
    private Map<String, List<TableRow>> approvalsOf(List<String> approving) {
        String approved = column(ApprovalField.APPROVING);
        return groupedBy(listIn(column(ApprovalField.TABLE), approved, approving, "",
                records.columns(ApprovalField.values()), APPROVALS_PER_RECORD, "read the approvals"), approved);
    }

    private List<TableRow> requestedOf(String approvingId, String number) {
        return api.list(column(ApprovalField.TABLE), column(ApprovalField.APPROVING) + "=" + approvingId + "^"
                        + column(ApprovalField.STATE) + "IN" + String.join(",", records.requested()),
                records.columns(ApprovalField.values()), APPROVALS_PER_RECORD, "read the approvals of " + number);
    }

    private boolean open(TableRow task) {
        return !records.taskStateOf(task, TaskState.OPEN).frozen();
    }

    private String column(ProTechFields.Field field) {
        return fields.column(field);
    }

    private String linkTo(String number) {
        return url + "/" + column(ChangeField.TABLE) + ".do?sysparm_query=" + column(ChangeField.NUMBER) + "="
                + number;
    }

    /** The number ProTech holds for a change it found, or {@code fallback} when it did not answer one. */
    private String heldNumber(TableRow change, String fallback) {
        return Objects.requireNonNullElse(change.value(column(ChangeField.NUMBER)), fallback);
    }

    private static String numberOf(TableRow created, String column) {
        String number = created.value(column);
        if (number == null) {
            throw new UncheckedIOException(NO_NUMBER, new IOException(NO_NUMBER));
        }
        return number;
    }

    private static ChangeField approverOf(ApprovalRole role) {
        return switch (role) {
            case BUSINESS -> BUSINESS_APPROVER;
            case L1 -> L1_MANAGER;
            case L2 -> L2_MANAGER;
            case SUPPORT -> SUPPORT_APPROVER;
        };
    }

    private static Map<String, TableRow> byValue(List<TableRow> rows, String column) {
        return rows.stream().filter(row -> row.value(column) != null)
                .collect(toMap(row -> row.value(column), Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
    }

    private static Map<String, List<TableRow>> groupedBy(List<TableRow> rows, String column) {
        return rows.stream().filter(row -> row.value(column) != null)
                .collect(groupingBy(row -> row.value(column), LinkedHashMap::new, toList()));
    }

    private static List<String> numbers(Stream<String> numbers) {
        return numbers.map(LiveProTechAdapter::safe).filter(Objects::nonNull).distinct().toList();
    }

    /** A number as ProTech spells them: letters, digits, {@code _} and {@code -}, nothing that ends a query. */
    private static String safe(String number) {
        return trimToNull(trimToEmpty(number).replaceAll("[^A-Za-z0-9_-]", ""));
    }
}

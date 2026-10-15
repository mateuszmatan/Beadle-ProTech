package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeFields;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.LookupTable;
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.TaskFields;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRole;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.ProTechMode;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;
import com.bbh.itss.beadle.protech.domain.change.TaskState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.adapter.out.protech.ServiceNowRow.SYS_ID;
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.MAX_TASKS;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CI;
import static org.apache.commons.lang3.Strings.CS;

/**
 * The live ProTech: speaks the ServiceNow Table API of BBH's ServiceNow instance as the Beadle service account,
 * used when {@code beadle.protech.url} names it. A change is a {@code change_request}, its tasks are
 * {@code change_task} records that reference it, and the approvals of both are {@code sysapproval_approver} records;
 * the lookups search the tables of {@code beadle.protech.fields.lookups}. Which ServiceNow field holds which field of
 * Beadle, and which state values stand for which state, is {@link ProTechFields}, documented in
 * {@code docs/PROTECH-MAPPING.md}.
 */
@Component
@ConditionalOnExpression(ProTechProperties.CONFIGURED)
class ServiceNowTableApiAdapter implements ProTechPort, ProTechLookupPort {

    static final int APPROVALS_PER_RECORD = 20;
    static final String NO_NUMBER = "ProTech answered without the number of the new record";

    private final ServiceNowTableApi api;
    private final ServiceNowRecords records;
    private final ProTechFields fields;
    private final String url;
    private final Clock clock;

    ServiceNowTableApiAdapter(ProTechProperties properties, RestClient.Builder builder, Clock clock) {
        this.api = new ServiceNowTableApi(builder, properties);
        this.fields = properties.fields();
        this.records = new ServiceNowRecords(fields);
        this.url = CS.removeEnd(trimToEmpty(properties.url()), "/");
        this.clock = clock;
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public ProTechMode mode() {
        return ProTechMode.LIVE;
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        ChangeFields columns = fields.change();
        ServiceNowRow created = api.create(columns.table(), records.changeBody(change),
                List.of(SYS_ID, columns.number()), "raise the change");
        String number = numberOf(created, columns.number());
        return new RaisedChange(number, linkTo(number));
    }

    @Override
    public String createTask(String changeNumber, ChangeTask task) {
        String number = changeRow(changeNumber, List.of(SYS_ID)).value(fields.change().number());
        return insertTask(number == null ? changeNumber : number, task);
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        ChangeFields columns = fields.change();
        List<String> numbers = known.stream().map(ProductionChange::number).map(ServiceNowTableApiAdapter::safe)
                .filter(Objects::nonNull).distinct().toList();
        if (numbers.isEmpty()) {
            return Map.of();
        }
        Map<String, ServiceNowRow> changes = api.list(columns.table(), columns.number() + "IN" + String.join(",",
                        numbers), records.changeColumns(), numbers.size(), "read the changes " + String.join(", ", numbers))
                .stream().filter(row -> row.value(columns.number()) != null)
                .collect(toMap(row -> row.value(columns.number()), row -> row, (first, second) -> first));
        if (changes.isEmpty()) {
            return Map.of();
        }
        List<String> changeIds = changes.values().stream().map(ServiceNowRow::sysId).toList();
        Map<String, List<ServiceNowRow>> tasks = tasksOf(changeIds);
        List<String> approving = Stream.concat(changeIds.stream(), tasks.values().stream().flatMap(List::stream)
                .map(ServiceNowRow::sysId)).filter(Objects::nonNull).toList();
        Map<String, List<ServiceNowRow>> approvals = approvalsOf(approving);
        Map<String, ProductionChange> read = new LinkedHashMap<>();
        for (ProductionChange change : known) {
            ServiceNowRow row = changes.get(safe(change.number()));
            if (row != null) {
                read.put(change.number(), records.changeOf(change, row, tasks.getOrDefault(row.sysId(), List.of()),
                        approvals, linkTo(row.value(columns.number())), now(clock)));
            }
        }
        return read;
    }

    @Override
    public void update(ProductionChange change) {
        ChangeFields columns = fields.change();
        TaskFields taskColumns = fields.task();
        ServiceNowRow row = changeRow(change.number(), List.of(SYS_ID));
        String number = row.value(columns.number()) == null ? change.number() : row.value(columns.number());
        api.update(columns.table(), row.sysId(), records.changeBody(change), true, "update " + number);
        Map<String, ServiceNowRow> held = new LinkedHashMap<>();
        tasksOf(List.of(row.sysId())).getOrDefault(row.sysId(), List.of())
                .forEach(task -> held.putIfAbsent(task.value(taskColumns.number()), task));
        for (ChangeTask task : change.tasks()) {
            ServiceNowRow found = task.number() == null ? null : held.remove(task.number());
            if (found == null) {
                insertTask(number, task);
            } else if (!records.taskStateOf(found, TaskState.OPEN).frozen()) {
                api.update(taskColumns.table(), found.sysId(), records.taskBody(null, task), true,
                        "update " + task.number() + " of " + number);
            }
        }
        held.forEach((task, dropped) -> {
            if (!records.taskStateOf(dropped, TaskState.OPEN).frozen()) {
                api.update(taskColumns.table(), dropped.sysId(), records.canceled(), false,
                        "cancel " + task + " of " + number);
            }
        });
    }

    @Override
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        ChangeFields columns = fields.change();
        List<String> approverColumns = Stream.of(columns.l1Manager(), columns.l2Manager(),
                columns.businessApprover(), columns.supportApprover()).filter(column -> !column.isEmpty()).toList();
        ServiceNowRow change = changeRow(changeNumber, Stream.concat(Stream.of(SYS_ID), approverColumns.stream())
                .toList());
        String number = Objects.requireNonNullElse(change.value(columns.number()), changeNumber);
        List<ServiceNowRow> waiting;
        String reminder;
        if (approval.role() != null) {
            String approver = change.display(approverColumnOf(approval.role()));
            waiting = approver == null ? List.of() : requestedOf(change.sysId(), number).stream()
                    .filter(row -> CI.equals(row.display(fields.approval().approver()), approver)).toList();
            reminder = "Beadle reminds you that " + number + " waits for your approval as its "
                    + approval.role().label() + ".";
        } else {
            ServiceNowRow task = taskRow(change.sysId(), number, approval.task());
            waiting = requestedOf(task.sysId(), approval.task());
            reminder = "Beadle reminds you that the change task " + approval.task() + " of " + number
                    + " waits for your approval.";
        }
        if (waiting.isEmpty()) {
            throw new IllegalStateException("ProTech has nobody to remind on " + number);
        }
        for (ServiceNowRow row : waiting) {
            api.update(fields.approval().table(), row.sysId(), Map.of(fields.approval().comments(), reminder), true,
                    "remind " + row.display(fields.approval().approver()) + " on " + number);
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

    private String insertTask(String changeNumber, ChangeTask task) {
        TaskFields columns = fields.task();
        ServiceNowRow created = api.create(columns.table(), records.taskBody(changeNumber, task),
                List.of(SYS_ID, columns.number()), "create a change task on " + changeNumber);
        return numberOf(created, columns.number());
    }

    private ServiceNowRow changeRow(String changeNumber, List<String> columns) {
        ChangeFields change = fields.change();
        String number = safe(changeNumber);
        List<String> wanted = Stream.concat(columns.stream(), Stream.of(change.number())).distinct().toList();
        return number == null ? missing(changeNumber) : api.list(change.table(), change.number() + "=" + number,
                        wanted, 1, "find " + number).stream().findFirst()
                .orElseGet(() -> missing(changeNumber));
    }

    private ServiceNowRow taskRow(String changeId, String changeNumber, String taskNumber) {
        TaskFields task = fields.task();
        String number = safe(taskNumber);
        List<ServiceNowRow> found = number == null ? List.of() : api.list(task.table(), task.change() + "="
                + changeId + "^" + task.number() + "=" + number, List.of(SYS_ID, task.number()), 1, "find " + number);
        return found.stream().findFirst().orElseThrow(() -> new IllegalStateException("ProTech has no change task "
                + taskNumber + " on " + changeNumber));
    }

    private Map<String, List<ServiceNowRow>> tasksOf(List<String> changeIds) {
        TaskFields task = fields.task();
        return api.list(task.table(), task.change() + "IN" + String.join(",", changeIds) + "^ORDERBY"
                                + task.number(), records.taskColumns(), MAX_TASKS * 2 * changeIds.size(),
                        "read the change tasks").stream()
                .filter(row -> row.value(task.change()) != null)
                .collect(groupingBy(row -> row.value(task.change()), LinkedHashMap::new,
                        toList()));
    }

    private Map<String, List<ServiceNowRow>> approvalsOf(List<String> approving) {
        String column = fields.approval().approving();
        return api.list(fields.approval().table(), column + "IN" + String.join(",", approving),
                        records.approvalColumns(), APPROVALS_PER_RECORD * approving.size(), "read the approvals")
                .stream().filter(row -> row.value(column) != null)
                .collect(groupingBy(row -> row.value(column), LinkedHashMap::new, toList()));
    }

    private List<ServiceNowRow> requestedOf(String approvingId, String number) {
        String state = fields.approval().state();
        return api.list(fields.approval().table(), fields.approval().approving() + "=" + approvingId + "^" + state
                        + "IN" + String.join(",", records.requested()), records.approvalColumns(),
                APPROVALS_PER_RECORD, "read the approvals of " + number);
    }

    private String approverColumnOf(ApprovalRole role) {
        ChangeFields columns = fields.change();
        return switch (role) {
            case BUSINESS -> columns.businessApprover();
            case L1 -> columns.l1Manager();
            case L2 -> columns.l2Manager();
            case SUPPORT -> columns.supportApprover();
        };
    }

    private String linkTo(String number) {
        return url + "/" + fields.change().table() + ".do?sysparm_query=" + fields.change().number() + "=" + number;
    }

    private static String numberOf(ServiceNowRow created, String column) {
        String number = created.value(column);
        if (number == null) {
            throw new UncheckedIOException(NO_NUMBER, new IOException(NO_NUMBER));
        }
        return number;
    }

    private static ServiceNowRow missing(String changeNumber) {
        throw new IllegalStateException("ProTech has no change " + changeNumber);
    }

    /** A number as ProTech spells them: letters, digits, {@code _} and {@code -}, nothing that ends a query. */
    private static String safe(String number) {
        return trimToNull(trimToEmpty(number).replaceAll("[^A-Za-z0-9_-]", ""));
    }
}

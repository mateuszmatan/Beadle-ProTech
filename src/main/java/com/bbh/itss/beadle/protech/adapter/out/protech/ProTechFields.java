package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.domain.change.ApprovalState;
import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.TaskState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JsonPointer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Conversion.DATE_TIME;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Conversion.FLAG;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Conversion.KEY;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Conversion.TEXT;
import static com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.Conversion.WRITTEN;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.REQUESTED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CTASK_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.ESCALATED_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.SECONDARY_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.SUPPORT_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.ASSIGNMENT_GROUPS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.BUSINESS_SERVICES;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CLIENTS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CONFIGURATION_ITEMS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.DEPARTMENTS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.INCIDENTS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.PROBLEMS;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.RELEASES;
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.USERS;
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED;
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN;
import static com.bbh.itss.beadle.protech.domain.change.TaskState.WORK_IN_PROGRESS;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * The tables, fields and state values of ProTech the live adapter uses, bound from {@code beadle.protech.fields.*}.
 * Every name defaults to the base system of ServiceNow, on which ProTech runs, or, for the fields BBH added, the
 * {@code u_} convention; a property left out keeps its default and one set empty leaves its field out of every call,
 * so that the real instance is matched without a code change. docs/PROTECH-MAPPING.md lists them all.
 */
public record ProTechFields(Map<ChangeField, String> change, Map<TaskField, String> task,
                            Map<ApprovalField, String> approval, Map<ChangeState, List<String>> changeStates,
                            Map<TaskState, List<String>> taskStates, Map<ApprovalState, List<String>> approvalStates,
                            Map<LookupKind, LookupTable> lookups) {

    static final Map<ChangeState, List<String>> CHANGE_STATES = Map.of(
            DRAFT, List.of("-5", "New", "Draft"),
            BUSINESS_APPROVAL, List.of("Business Approval"),
            PRIMARY_APPROVAL, List.of("-4", "Assess", "Primary Approval"),
            SECONDARY_APPROVAL, List.of("Secondary Approval"),
            SUPPORT_APPROVAL, List.of("-3", "Authorize", "Support Approval"),
            CTASK_APPROVAL, List.of("CTask Approval"),
            ESCALATED_APPROVAL, List.of("Escalated Approval"),
            IMPLEMENTATION, List.of("-2", "Scheduled", "-1", "Implement", "In Progress"),
            CLOSED, List.of("3", "Closed", "0", "Review", "4", "Canceled"));
    static final Map<TaskState, List<String>> TASK_STATES = Map.of(
            OPEN, List.of("1", "Open", "-5", "Pending"),
            WORK_IN_PROGRESS, List.of("2", "Work in Progress", "In Progress"),
            TaskState.CLOSED, List.of("3", "Closed Complete", "Closed", "7", "Closed Skipped"),
            CANCELED, List.of("4", "Canceled", "Closed Incomplete"));
    static final Map<ApprovalState, List<String>> APPROVAL_STATES = Map.of(
            REQUESTED, List.of("requested"),
            APPROVED, List.of("approved", "not_required"));
    static final Map<LookupKind, LookupTable> LOOKUPS = Map.of(
            USERS, new LookupTable("sys_user", "name", "email", "active=true"),
            BUSINESS_SERVICES, new LookupTable("cmn_department", "name", "cost_center", ""),
            DEPARTMENTS, new LookupTable("cmn_department", "name", "cost_center", ""),
            ASSIGNMENT_GROUPS, new LookupTable("sys_user_group", "name", "description", "active=true"),
            RELEASES, new LookupTable("rm_release", "short_description", "number", ""),
            CONFIGURATION_ITEMS, new LookupTable("cmdb_ci", "name", "sys_class_name", ""),
            INCIDENTS, new LookupTable("incident", "number", "short_description", ""),
            PROBLEMS, new LookupTable("problem", "number", "short_description", ""),
            CLIENTS, new LookupTable("core_company", "name", "country", "customer=true"));
    public static final ProTechFields DEFAULTS = new ProTechFields(null, null, null, null, null, null, null);

    public ProTechFields {
        change = merged(columns(ChangeField.values()), change);
        task = merged(columns(TaskField.values()), task);
        approval = merged(columns(ApprovalField.values()), approval);
        changeStates = merged(CHANGE_STATES, changeStates);
        taskStates = merged(TASK_STATES, taskStates);
        approvalStates = merged(APPROVAL_STATES, approvalStates);
        lookups = merged(LOOKUPS, lookups == null ? null : lookups.entrySet().stream()
                .filter(table -> table.getValue() != null).collect(toMap(Map.Entry::getKey,
                        table -> table.getValue().or(LOOKUPS.get(table.getKey())))));
    }

    /** The name of a field in ProTech; empty when the field is left out of every call. */
    String column(Field field) {
        return switch (field) {
            case ChangeField known -> change.get(known);
            case TaskField known -> task.get(known);
            case ApprovalField known -> approval.get(known);
        };
    }

    private static <K extends Enum<K> & Field> Map<K, String> columns(K[] fields) {
        return Stream.of(fields).collect(toMap(field -> field, Field::column));
    }

    /** The defaults with every value configured: a text trimmed, so that an empty one leaves the field out. */
    @SuppressWarnings("unchecked")
    private static <K extends Enum<K>, V> Map<K, V> merged(Map<K, V> defaults, Map<K, V> configured) {
        Map<K, V> merged = new EnumMap<>(defaults);
        if (configured != null) {
            configured.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value instanceof String text ? (V) trimToEmpty(text) : value);
                }
            });
        }
        return unmodifiableMap(merged);
    }

    /** How a field of Beadle travels in a field of ProTech. */
    enum Conversion {
        /** A text, written and read as the display value. */
        TEXT,
        /** An instant, written and read as {@code yyyy-MM-dd HH:mm:ss} in UTC. */
        DATE_TIME,
        /** The type of a change, written as its label and read by label or name. */
        TYPE,
        /** A flag, written {@code true} or {@code false}, read true for {@code true}, {@code 1} or {@code yes}. */
        FLAG,
        /** Written only, as text: what ProTech answers is not read back. */
        WRITTEN,
        /** Read and written by the adapter itself: a number, a state, a reference. */
        KEY,
        /** The name of the table, not a field of it. */
        TABLE;

        /** Whether the field is read back into Beadle's record, at its path. */
        boolean read() {
            return compareTo(WRITTEN) < 0;
        }

        /** Whether the field is asked for when the adapter reads the table. */
        boolean listed() {
            return this != WRITTEN && this != TABLE;
        }
    }

    /** A field of a ProTech table: where Beadle holds it (none for a key), its default name and how it travels. */
    sealed interface Field permits ChangeField, TaskField, ApprovalField {

        String path();

        String column();

        Conversion conversion();

        /** Where Beadle holds the field in the JSON of its record, such as {@code /template/approvers/l1Manager}. */
        default JsonPointer pointer() {
            return JsonPointer.compile("/" + path().replace('.', '/'));
        }
    }

    /** The fields of a change in {@code change_request}. */
    @Getter
    @RequiredArgsConstructor
    public enum ChangeField implements Field {
        TABLE(null, "change_request", Conversion.TABLE),
        NUMBER(null, "number", KEY),
        SHORT_DESCRIPTION("shortDescription", "short_description", TEXT),
        DESCRIPTION("description", "description", TEXT),
        INSTALLATION_START("schedule.installationStart", "start_date", DATE_TIME),
        INSTALLATION_END("schedule.installationEnd", "end_date", DATE_TIME),
        VALIDATION_START("schedule.validationStart", "u_validation_start", DATE_TIME),
        VALIDATION_END("schedule.validationEnd", "u_validation_end", DATE_TIME),
        FIRST_USAGE("schedule.firstUsage", "u_first_usage", DATE_TIME),
        DOWNTIME_START("schedule.downtimeStart", "u_downtime_start", DATE_TIME),
        DOWNTIME_END("schedule.downtimeEnd", "u_downtime_end", DATE_TIME),
        REQUESTED_FOR("template.requestedFor", "u_requested_for", TEXT),
        REQUESTED_BY("template.requestedBy", "requested_by", TEXT),
        DEPARTMENT("template.department", "u_department", TEXT),
        ASSIGNMENT_GROUP("template.assignmentGroup", "assignment_group", TEXT),
        CATEGORY("template.category", "category", TEXT),
        ASSIGNED_TO("template.assignedTo", "assigned_to", TEXT),
        TYPE("template.type", "type", Conversion.TYPE),
        RELEASE("template.release", "u_release", TEXT),
        CONFIGURATION_ITEM("template.configurationItem", "cmdb_ci", TEXT),
        INCIDENT("template.incident", "u_incident", TEXT),
        DIRECT_BUSINESS_SERVICE("template.directBusinessService", "business_service", TEXT),
        PROBLEM("template.problem", "u_problem", TEXT),
        RISK("template.risk", "risk", WRITTEN),
        AFFECTED_CLIENTS("template.affectedClients", "u_affected_clients", TEXT),
        USERS_AFFECTED("template.usersAffected", "u_users_affected", TEXT),
        L1_MANAGER("template.approvers.l1Manager", "u_l1_manager", TEXT),
        L2_MANAGER("template.approvers.l2Manager", "u_l2_manager", TEXT),
        BUSINESS_APPROVER("template.approvers.businessApprover", "u_business_approver", TEXT),
        SUPPORT_APPROVER("template.approvers.supportApprover", "u_support_approver", TEXT),
        DOWNTIME("template.downtime", "u_downtime", FLAG),
        TEST_SUMMARY("template.planning.testSummary", "test_plan", TEXT),
        IMPLEMENTATION_PLAN("template.planning.implementationPlan", "implementation_plan", TEXT),
        VALIDATION_PLAN("template.planning.validationPlan", "u_validation_plan", TEXT),
        BACKOUT_PLAN("template.planning.backoutPlan", "backout_plan", TEXT),
        FIRST_USE_PLAN("template.planning.firstUsePlan", "u_first_use_plan", TEXT),
        PRIVILEGED_ACCESS("template.privilegedAccess", "u_privileged_access", WRITTEN),
        RISK_ASSESSMENT("template.riskAssessment", "u_risk_assessment", WRITTEN),
        SECURE_CODING_TICKET("template.secureCodingTicket", "u_secure_coding_ticket", TEXT),
        STATE(null, "state", KEY);

        private final String path;
        private final String column;
        private final Conversion conversion;
    }

    /** The fields of a change task in {@code change_task}. */
    @Getter
    @RequiredArgsConstructor
    public enum TaskField implements Field {
        TABLE(null, "change_task", Conversion.TABLE),
        NUMBER(null, "number", KEY),
        CHANGE(null, "change_request", KEY),
        ASSIGNMENT_GROUP("details.assignmentGroup", "assignment_group", TEXT),
        ASSIGNED_TO("details.assignedTo", "assigned_to", TEXT),
        CONFIGURATION_ITEM("details.configurationItem", "cmdb_ci", TEXT),
        PLATFORM("details.platform", "u_platform", TEXT),
        APPLICATION("details.application", "u_application", TEXT),
        PACKAGES("details.packages", "u_packages", TEXT),
        BACKOUT_PACKAGES("details.backoutPackages", "u_backout_packages", TEXT),
        IMPORTANCE("details.importance", "priority", TEXT),
        SHORT_DESCRIPTION("details.shortDescription", "short_description", TEXT),
        DESCRIPTION("details.description", "description", TEXT),
        ADDITIONAL_COMMENTS("details.additionalComments", "u_additional_comments", TEXT),
        START("start", "expected_start", DATE_TIME),
        STATE(null, "state", KEY),
        APPROVAL(null, "approval", KEY);

        private final String path;
        private final String column;
        private final Conversion conversion;
    }

    /** The fields of an approval in {@code sysapproval_approver}; a reminder is a comment on the approval. */
    @Getter
    @RequiredArgsConstructor
    public enum ApprovalField implements Field {
        TABLE("sysapproval_approver", Conversion.TABLE),
        APPROVING("sysapproval", KEY),
        APPROVER("approver", KEY),
        STATE("state", KEY),
        COMMENTS("comments", WRITTEN);

        private final String column;
        private final Conversion conversion;

        @Override
        public String path() {
            return null;
        }
    }

    /**
     * Where a lookup searches: the table, the field shown as the value, the field shown as its detail and a filter
     * in the encoded query syntax of ProTech that every search adds. A name left out takes the default of its kind.
     */
    public record LookupTable(String table, String value, String detail, String filter) {

        LookupTable or(LookupTable defaults) {
            return new LookupTable(column(table, defaults.table), column(value, defaults.value),
                    column(detail, defaults.detail), column(filter, defaults.filter));
        }

        private static String column(String configured, String fallback) {
            return configured == null ? fallback : trimToEmpty(configured);
        }
    }
}

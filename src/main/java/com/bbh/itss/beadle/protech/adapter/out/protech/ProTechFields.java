package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.domain.change.ApprovalState;
import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.TaskState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * The names of the ServiceNow tables and fields the live adapter reads and writes, and the values of their states,
 * bound from {@code beadle.protech.fields.*}. Every name has a default that follows the ServiceNow base system or,
 * for the fields BBH added to ProTech, the {@code u_} convention; a property left out keeps the default and a
 * property set to an empty value leaves that field out of every call, so that the real instance is matched without
 * a code change. {@code docs/PROTECH-MAPPING.md} lists them all.
 */
public record ProTechFields(ChangeFields change, TaskFields task, ApprovalFields approval,
                            Map<ChangeState, List<String>> changeStates, Map<TaskState, List<String>> taskStates,
                            Map<ApprovalState, List<String>> approvalStates, Map<LookupKind, LookupTable> lookups) {

    static final Map<ChangeState, List<String>> CHANGE_STATES = ordered(ChangeState.class, Map.of(
            DRAFT, List.of("-5", "New", "Draft"),
            BUSINESS_APPROVAL, List.of("Business Approval"),
            PRIMARY_APPROVAL, List.of("-4", "Assess", "Primary Approval"),
            SECONDARY_APPROVAL, List.of("Secondary Approval"),
            SUPPORT_APPROVAL, List.of("-3", "Authorize", "Support Approval"),
            CTASK_APPROVAL, List.of("CTask Approval"),
            ESCALATED_APPROVAL, List.of("Escalated Approval"),
            IMPLEMENTATION, List.of("-2", "Scheduled", "-1", "Implement", "In Progress"),
            CLOSED, List.of("3", "Closed", "0", "Review", "4", "Canceled")));
    static final Map<TaskState, List<String>> TASK_STATES = ordered(TaskState.class, Map.of(
            OPEN, List.of("1", "Open", "-5", "Pending"),
            WORK_IN_PROGRESS, List.of("2", "Work in Progress", "In Progress"),
            TaskState.CLOSED, List.of("3", "Closed Complete", "Closed", "7", "Closed Skipped"),
            CANCELED, List.of("4", "Canceled", "Closed Incomplete")));
    static final Map<ApprovalState, List<String>> APPROVAL_STATES = ordered(ApprovalState.class, Map.of(
            REQUESTED, List.of("requested"),
            APPROVED, List.of("approved", "not_required")));
    static final Map<LookupKind, LookupTable> LOOKUPS = ordered(LookupKind.class, Map.of(
            USERS, new LookupTable("sys_user", "name", "email", "active=true"),
            DEPARTMENTS, new LookupTable("cmn_department", "name", "cost_center", ""),
            ASSIGNMENT_GROUPS, new LookupTable("sys_user_group", "name", "description", "active=true"),
            RELEASES, new LookupTable("rm_release", "short_description", "number", ""),
            CONFIGURATION_ITEMS, new LookupTable("cmdb_ci", "name", "sys_class_name", ""),
            INCIDENTS, new LookupTable("incident", "number", "short_description", ""),
            PROBLEMS, new LookupTable("problem", "number", "short_description", ""),
            CLIENTS, new LookupTable("core_company", "name", "country", "customer=true")));
    public static final ProTechFields DEFAULTS = new ProTechFields(null, null, null, null, null, null, null);

    public ProTechFields {
        change = getIfNull(change, ChangeFields.DEFAULTS);
        task = getIfNull(task, TaskFields.DEFAULTS);
        approval = getIfNull(approval, ApprovalFields.DEFAULTS);
        changeStates = merged(ChangeState.class, CHANGE_STATES, changeStates);
        taskStates = merged(TaskState.class, TASK_STATES, taskStates);
        approvalStates = merged(ApprovalState.class, APPROVAL_STATES, approvalStates);
        lookups = merged(LookupKind.class, LOOKUPS, lookups == null ? null : completed(lookups));
    }

    private static <K extends Enum<K>, V> Map<K, V> ordered(Class<K> keys, Map<K, V> values) {
        return merged(keys, values, Map.of());
    }

    private static <K extends Enum<K>, V> Map<K, V> merged(Class<K> keys, Map<K, V> defaults, Map<K, V> configured) {
        Map<K, V> merged = new EnumMap<>(keys);
        merged.putAll(defaults);
        if (configured != null) {
            configured.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        return unmodifiableMap(merged);
    }

    private static Map<LookupKind, LookupTable> completed(Map<LookupKind, LookupTable> configured) {
        Map<LookupKind, LookupTable> completed = new EnumMap<>(LookupKind.class);
        configured.forEach((kind, table) -> completed.put(kind, table == null ? null : table.or(LOOKUPS.get(kind))));
        return completed;
    }

    static String column(String configured, String fallback) {
        return configured == null ? fallback : trimToEmpty(configured);
    }

    /** The fields of a change in {@code change_request}. */
    public record ChangeFields(String table, String number, String shortDescription, String description,
                               String installationStart, String installationEnd, String validationStart,
                               String validationEnd, String firstUsage, String downtimeStart, String downtimeEnd,
                               String requestedFor, String requestedBy, String department, String assignmentGroup,
                               String category, String assignedTo, String type, String release,
                               String configurationItem, String incident, String directBusinessService,
                               String problem, String risk, String affectedClients, String usersAffected,
                               String l1Manager, String l2Manager, String businessApprover, String supportApprover,
                               String downtime, String testSummary, String implementationPlan, String validationPlan,
                               String backoutPlan, String firstUsePlan, String privilegedAccess,
                               String riskAssessment, String secureCodingTicket, String state) {

        public static final ChangeFields DEFAULTS = new ChangeFields(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        public ChangeFields {
            table = column(table, "change_request");
            number = column(number, "number");
            shortDescription = column(shortDescription, "short_description");
            description = column(description, "description");
            installationStart = column(installationStart, "start_date");
            installationEnd = column(installationEnd, "end_date");
            validationStart = column(validationStart, "u_validation_start");
            validationEnd = column(validationEnd, "u_validation_end");
            firstUsage = column(firstUsage, "u_first_usage");
            downtimeStart = column(downtimeStart, "u_downtime_start");
            downtimeEnd = column(downtimeEnd, "u_downtime_end");
            requestedFor = column(requestedFor, "u_requested_for");
            requestedBy = column(requestedBy, "requested_by");
            department = column(department, "u_department");
            assignmentGroup = column(assignmentGroup, "assignment_group");
            category = column(category, "category");
            assignedTo = column(assignedTo, "assigned_to");
            type = column(type, "type");
            release = column(release, "u_release");
            configurationItem = column(configurationItem, "cmdb_ci");
            incident = column(incident, "u_incident");
            directBusinessService = column(directBusinessService, "business_service");
            problem = column(problem, "u_problem");
            risk = column(risk, "risk");
            affectedClients = column(affectedClients, "u_affected_clients");
            usersAffected = column(usersAffected, "u_users_affected");
            l1Manager = column(l1Manager, "u_l1_manager");
            l2Manager = column(l2Manager, "u_l2_manager");
            businessApprover = column(businessApprover, "u_business_approver");
            supportApprover = column(supportApprover, "u_support_approver");
            downtime = column(downtime, "u_downtime");
            testSummary = column(testSummary, "test_plan");
            implementationPlan = column(implementationPlan, "implementation_plan");
            validationPlan = column(validationPlan, "u_validation_plan");
            backoutPlan = column(backoutPlan, "backout_plan");
            firstUsePlan = column(firstUsePlan, "u_first_use_plan");
            privilegedAccess = column(privilegedAccess, "u_privileged_access");
            riskAssessment = column(riskAssessment, "u_risk_assessment");
            secureCodingTicket = column(secureCodingTicket, "u_secure_coding_ticket");
            state = column(state, "state");
        }
    }

    /** The fields of a change task in {@code change_task}. */
    public record TaskFields(String table, String number, String change, String assignmentGroup, String assignedTo,
                             String configurationItem, String platform, String application, String packages,
                             String backoutPackages, String importance, String shortDescription, String description,
                             String additionalComments, String start, String state, String approval) {

        public static final TaskFields DEFAULTS = new TaskFields(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        public TaskFields {
            table = column(table, "change_task");
            number = column(number, "number");
            change = column(change, "change_request");
            assignmentGroup = column(assignmentGroup, "assignment_group");
            assignedTo = column(assignedTo, "assigned_to");
            configurationItem = column(configurationItem, "cmdb_ci");
            platform = column(platform, "u_platform");
            application = column(application, "u_application");
            packages = column(packages, "u_packages");
            backoutPackages = column(backoutPackages, "u_backout_packages");
            importance = column(importance, "priority");
            shortDescription = column(shortDescription, "short_description");
            description = column(description, "description");
            additionalComments = column(additionalComments, "u_additional_comments");
            start = column(start, "expected_start");
            state = column(state, "state");
            approval = column(approval, "approval");
        }
    }

    /** The fields of an approval in {@code sysapproval_approver}; a reminder is a comment on the approval. */
    public record ApprovalFields(String table, String approving, String approver, String state, String comments) {

        public static final ApprovalFields DEFAULTS = new ApprovalFields(null, null, null, null, null);

        public ApprovalFields {
            table = column(table, "sysapproval_approver");
            approving = column(approving, "sysapproval");
            approver = column(approver, "approver");
            state = column(state, "state");
            comments = column(comments, "comments");
        }
    }

    /**
     * Where a lookup searches: the table, the field shown as the value, the field shown as its detail and a filter
     * in the encoded query syntax of ServiceNow that every search adds. A name left out takes the default of its kind.
     */
    public record LookupTable(String table, String value, String detail, String filter) {

        LookupTable or(LookupTable defaults) {
            return new LookupTable(column(table, defaults.table), column(value, defaults.value),
                    column(detail, defaults.detail), column(filter, defaults.filter));
        }
    }
}

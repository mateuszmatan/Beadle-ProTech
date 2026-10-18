package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.regex.Pattern;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.StringUtils.upperCase;

@Builder(toBuilder = true)
public record ChangeTemplate(String jiraProjectKey, String requestedFor, String requestedBy, String businessService,
                             String assignmentGroup, String category, String assignedTo, Type type, String release,
                             String configurationItem, String incident, String directBusinessService, String problem,
                             String risk, String affectedClients, String usersAffected,
                             Approvers approvers, Boolean downtime, Timing timing, Planning planning,
                             PrivilegedAccess privilegedAccess, RiskAssessment riskAssessment,
                             String secureCodingTicket, SecureCoding secureCoding) {

    public static final int GROUP_MAX = 200;
    public static final int NAME_MAX = 100;
    public static final int NUMBER_MAX = 40;
    public static final int TEXT_MAX = 2000;
    public static final int MAX_HOURS = 72;
    public static final int MAX_PRIVILEGED_USERS = 7;
    public static final String REQUIRED = "is required";
    public static final List<String> CATEGORIES = List.of("Application", "Hardware", "Infrastructure",
            "System Software", "Network", "Telecom", "Data Amendment", "Desktop Software", "Storage", "Facilities",
            "Other", "Database");
    private static final Pattern JIRA_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{0,9}$");
    private static final Pattern TIME_OF_DAY = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        STANDARD("Standard"), EMERGENCY("Emergency"), BUSINESS_CRITICAL("Business Critical"), MODEL("Model");

        private final String label;
    }

    public ChangeTemplate {
        jiraProjectKey = upperCase(trimToNull(jiraProjectKey), ROOT);
        requestedFor = trimToNull(requestedFor);
        requestedBy = trimToNull(requestedBy);
        businessService = trimToNull(businessService);
        assignmentGroup = trimToNull(assignmentGroup);
        category = trimToNull(category);
        assignedTo = trimToNull(assignedTo);
        release = trimToNull(release);
        configurationItem = trimToNull(configurationItem);
        incident = trimToNull(incident);
        directBusinessService = trimToNull(directBusinessService);
        problem = trimToNull(problem);
        affectedClients = trimToNull(affectedClients);
        usersAffected = trimToNull(usersAffected);
        approvers = getIfNull(approvers, Approvers.NONE);
        downtime = isTrue(downtime);
        privilegedAccess = getIfNull(privilegedAccess, PrivilegedAccess.NONE);
        riskAssessment = getIfNull(riskAssessment, RiskAssessment.DEFAULTS);
        risk = riskAssessment.risk();
        secureCodingTicket = trimToNull(secureCodingTicket);
        secureCoding = getIfNull(secureCoding, SecureCoding.NONE);
    }

    public ChangeTemplate edited(ChangeTemplate changes) {
        return changes.toBuilder().jiraProjectKey(jiraProjectKey).type(type).timing(timing).secureCoding(secureCoding)
                .build();
    }

    public void validate(ValidationProblems problems) {
        problems.require("jiraProjectKey", jiraProjectKey, REQUIRED)
                .require("assignmentGroup", assignmentGroup, REQUIRED)
                .require("category", category, REQUIRED)
                .require("type", type, REQUIRED)
                .require("configurationItem", configurationItem, REQUIRED)
                .require("timing", timing, REQUIRED)
                .require("planning", planning, REQUIRED);
        if (jiraProjectKey != null && !JIRA_KEY.matcher(jiraProjectKey).matches()) {
            problems.add("jiraProjectKey", "must be a Jira project key such as CERT: up to 10 upper case letters, "
                    + "digits or _, starting with a letter");
        }
        if (category != null && !CATEGORIES.contains(category)) {
            problems.add("category", "must be one of " + String.join(", ", CATEGORIES));
        }
        problems.fits("requestedFor", requestedFor, GROUP_MAX)
                .fits("requestedBy", requestedBy, GROUP_MAX)
                .fits("businessService", businessService, NAME_MAX)
                .fits("assignmentGroup", assignmentGroup, GROUP_MAX)
                .fits("assignedTo", assignedTo, GROUP_MAX)
                .fits("release", release, NAME_MAX)
                .fits("configurationItem", configurationItem, GROUP_MAX)
                .fits("incident", incident, NUMBER_MAX)
                .fits("directBusinessService", directBusinessService, GROUP_MAX)
                .fits("problem", problem, NUMBER_MAX)
                .fits("affectedClients", affectedClients, TEXT_MAX)
                .fits("usersAffected", usersAffected, TEXT_MAX)
                .fits("secureCodingTicket", secureCodingTicket, NUMBER_MAX);
        approvers.validate(problems.at("approvers"));
        if (timing != null) {
            timing.validate(problems.at("timing"));
        }
        if (planning != null) {
            planning.validate(problems.at("planning"));
        }
        privilegedAccess.validate(problems.at("privilegedAccess"));
        riskAssessment.validate(problems.at("riskAssessment"));
        secureCoding.validate(problems.at("secureCoding"));
    }

    public record Approvers(String l1Manager, String l2Manager, String businessApprover, String supportApprover) {

        public static final Approvers NONE = new Approvers(null, null, null, null);

        public Approvers {
            l1Manager = trimToNull(l1Manager);
            l2Manager = trimToNull(l2Manager);
            businessApprover = trimToNull(businessApprover);
            supportApprover = trimToNull(supportApprover);
        }

        void validate(ValidationProblems problems) {
            problems.fits("l1Manager", l1Manager, GROUP_MAX)
                    .fits("l2Manager", l2Manager, GROUP_MAX)
                    .fits("businessApprover", businessApprover, GROUP_MAX)
                    .fits("supportApprover", supportApprover, GROUP_MAX);
        }
    }

    public record Timing(String installationStart, Integer installationHours, Integer validationHours) {

        public Timing {
            installationStart = trimToNull(installationStart);
        }

        void validate(ValidationProblems problems) {
            problems.require("installationStart", installationStart, REQUIRED)
                    .require("installationHours", installationHours, REQUIRED)
                    .require("validationHours", validationHours, REQUIRED);
            if (installationStart != null && !TIME_OF_DAY.matcher(installationStart).matches()) {
                problems.add("installationStart", "must be a time of day such as 18:00");
            }
            between(problems, "installationHours", installationHours, 1);
            between(problems, "validationHours", validationHours, 0);
        }

        private static void between(ValidationProblems problems, String field, Integer hours, int min) {
            if (hours != null && (hours < min || hours > MAX_HOURS)) {
                problems.add(field, "must be between " + min + " and " + MAX_HOURS + " hours");
            }
        }
    }

    public record Planning(String testSummary, String implementationPlan, String validationPlan,
                           String backoutPlan, String firstUsePlan) {

        public Planning {
            testSummary = trimToNull(testSummary);
            implementationPlan = trimToNull(implementationPlan);
            validationPlan = trimToNull(validationPlan);
            backoutPlan = trimToNull(backoutPlan);
            firstUsePlan = trimToNull(firstUsePlan);
        }

        void validate(ValidationProblems problems) {
            problems.require("testSummary", testSummary, REQUIRED)
                    .require("implementationPlan", implementationPlan, REQUIRED)
                    .require("validationPlan", validationPlan, REQUIRED)
                    .require("backoutPlan", backoutPlan, REQUIRED)
                    .require("firstUsePlan", firstUsePlan, REQUIRED)
                    .fits("testSummary", testSummary, TEXT_MAX)
                    .fits("implementationPlan", implementationPlan, TEXT_MAX)
                    .fits("validationPlan", validationPlan, TEXT_MAX)
                    .fits("backoutPlan", backoutPlan, TEXT_MAX)
                    .fits("firstUsePlan", firstUsePlan, TEXT_MAX);
        }
    }

    public record PrivilegedAccess(Boolean required, List<PrivilegedUser> users) {

        public static final PrivilegedAccess NONE = new PrivilegedAccess(false, List.of());

        public PrivilegedAccess {
            required = isTrue(required);
            users = emptyIfNull(users).stream().toList();
        }

        void validate(ValidationProblems problems) {
            if (!required && !users.isEmpty()) {
                problems.add("users", "must be empty when the change needs no privileged access");
            }
            if (required && users.isEmpty()) {
                problems.add("users", "add the users who need privileged access");
            }
            if (users.size() > MAX_PRIVILEGED_USERS) {
                problems.add("users", "may list at most " + MAX_PRIVILEGED_USERS + " users");
            }
            for (int index = 0; index < users.size(); index++) {
                PrivilegedUser user = users.get(index);
                if (user == null) {
                    problems.add("users[" + index + "]", REQUIRED);
                } else {
                    problems.at("users[" + index + "]").require("user", user.user(), REQUIRED)
                            .require("account", user.account(), REQUIRED)
                            .fits("user", user.user(), GROUP_MAX)
                            .fits("account", user.account(), GROUP_MAX);
                }
            }
        }

        /** How ProTech holds it: {@code Privileged access needed for: Ann Lee (adm_alee).} */
        public String text() {
            return !required ? "Privileged access: not needed." : "Privileged access needed for: "
                    + users.stream().map(PrivilegedUser::text).collect(joining(", ")) + ".";
        }
    }

    public record PrivilegedUser(String user, String account) {

        public PrivilegedUser {
            user = trimToNull(user);
            account = trimToNull(account);
        }

        String text() {
            return user + " (" + account + ")";
        }
    }
}

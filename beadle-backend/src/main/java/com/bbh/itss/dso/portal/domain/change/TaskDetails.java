package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;
import java.util.function.BiConsumer;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEXT_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CI;

@Builder(toBuilder = true)
public record TaskDetails(String assignmentGroup, String assignedTo, String configurationItem, String platform,
                          String application, String packages, String backoutPackages, String importance,
                          String shortDescription, String description, String additionalComments) {

    public static final int MAX_TASKS = 50;
    public static final String RELEASE_MANAGEMENT = "Release Management";
    public static final String OPENSHIFT = "OpenShift";
    public static final String OCP = "OCP";
    public static final List<String> PLATFORMS = List.of("None", "Mainframe", "Distributed", OPENSHIFT, "Cognos/Motio");
    public static final List<String> IMPORTANCES = List.of("1 - Critical", "2 - High", "3 - Moderate", "4 - Low",
            "5 - Planning");
    public static final String MODERATE = IMPORTANCES.get(2);

    public TaskDetails {
        assignmentGroup = trimToNull(assignmentGroup);
        assignedTo = trimToNull(assignedTo);
        configurationItem = trimToNull(configurationItem);
        shortDescription = trimToNull(shortDescription);
        description = trimToNull(description);
        additionalComments = trimToNull(additionalComments);
        boolean release = releaseManagement(assignmentGroup);
        platform = release ? getIfNull(trimToNull(platform), PLATFORMS.get(0)) : null;
        application = !release ? null : OPENSHIFT.equals(platform) ? OCP : trimToNull(application);
        packages = release ? trimToNull(packages) : null;
        backoutPackages = release ? trimToNull(backoutPackages) : null;
        importance = release ? null : getIfNull(trimToNull(importance), MODERATE);
    }

    public static List<TaskDetails> suggestedTasks(String productName, String supportGroup) {
        return List.of(builder().assignmentGroup(RELEASE_MANAGEMENT).application(abbreviateBytes(productName, NAME_MAX))
                        .shortDescription(abbreviateBytes("Deploy " + productName + " to production", SHORT_DESCRIPTION_MAX))
                        .description(abbreviateBytes("Deploy the release of " + productName + " in the change window with its"
                                + " deployment jobs, then run the smoke tests of the DevSecOps pipeline and record the"
                                + " result in this task.", DESCRIPTION_MAX)).build(),
                builder().assignmentGroup(supportGroup)
                        .shortDescription(abbreviateBytes("Validate " + productName + " in production", SHORT_DESCRIPTION_MAX))
                        .description(abbreviateBytes("Run the post-install validation of " + productName + ": the smoke tests"
                                + " and the monitoring, then confirm the release with the business owner in this"
                                + " task.", DESCRIPTION_MAX)).build());
    }

    public static void validateTasks(List<TaskDetails> tasks, ValidationProblems problems) {
        problems.require("tasks", tasks, "add at least one change task");
        validateEach(tasks, problems, TaskDetails::validate);
    }

    static <T> void validateEach(List<T> tasks, ValidationProblems problems, BiConsumer<T, ValidationProblems> check) {
        if (tasks.size() > MAX_TASKS) {
            problems.add("tasks", "may list at most " + MAX_TASKS + " change tasks");
        }
        for (int index = 0; index < tasks.size(); index++) {
            check.accept(tasks.get(index), problems.at("tasks[" + index + "]"));
        }
    }

    public boolean releaseManagement() {
        return releaseManagement(assignmentGroup);
    }

    TaskDetails within(String changeConfigurationItem) {
        return configurationItem == null ? toBuilder().configurationItem(changeConfigurationItem).build() : this;
    }

    void validate(ValidationProblems problems) {
        problems.require("assignmentGroup", assignmentGroup, REQUIRED)
                .require("shortDescription", shortDescription, REQUIRED)
                .require("description", description, REQUIRED)
                .fits("assignmentGroup", assignmentGroup, GROUP_MAX)
                .fits("assignedTo", assignedTo, GROUP_MAX)
                .fits("configurationItem", configurationItem, GROUP_MAX)
                .fits("application", application, NAME_MAX)
                .fits("packages", packages, TEXT_MAX)
                .fits("backoutPackages", backoutPackages, TEXT_MAX)
                .fits("shortDescription", shortDescription, SHORT_DESCRIPTION_MAX)
                .fits("description", description, DESCRIPTION_MAX)
                .fits("additionalComments", additionalComments, TEXT_MAX);
        oneOf("platform", platform, PLATFORMS, problems);
        oneOf("importance", importance, IMPORTANCES, problems);
    }

    private static boolean releaseManagement(String assignmentGroup) {
        return CI.contains(assignmentGroup, RELEASE_MANAGEMENT);
    }

    private static void oneOf(String field, String value, List<String> options, ValidationProblems problems) {
        if (value != null && !options.contains(value)) {
            problems.add(field, "must be one of " + String.join(", ", options));
        }
    }
}

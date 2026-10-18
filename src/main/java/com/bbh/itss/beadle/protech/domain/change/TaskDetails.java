package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.Builder;

import java.util.List;

import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.NAME_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.TEXT_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
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

    public boolean releaseManagement() {
        return releaseManagement(assignmentGroup);
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

package com.bbh.itss.dso.portal.domain.change;

import lombok.Builder;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Risk.LOW;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static java.util.Locale.ROOT;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.StringUtils.upperCase;

@Builder(toBuilder = true)
public record ChangeTemplate(String jiraProjectKey, String configurationItem, String assignmentGroup, Type type,
                             String category, Risk risk, Impact impact, String riskAssessment, List<String> approvers,
                             String description, String implementationPlan, String backoutPlan, String testPlan) {

    public static final String IMPLEMENTATION_PLAN = """
            1. Deploy each service with its change task, in the order listed.
            2. Run the smoke tests of the DevSecOps pipeline against production.
            3. Confirm with the business owner and close the change tasks.""";
    public static final String BACKOUT_PLAN = """
            Redeploy the previous release of each service from Nexus with the same deployment job \
            and confirm with the smoke tests.""";
    public static final String TEST_PLAN = """
            Unit, smoke, regression and performance tests and the security scans of the DevSecOps \
            pipeline passed on QC; Change Evidence in the DevSecOps portal holds the results.""";

    public enum Type { NORMAL, STANDARD, EMERGENCY }

    public enum Risk { LOW, MODERATE, HIGH }

    public enum Impact { LOW, MEDIUM, HIGH }

    public ChangeTemplate {
        jiraProjectKey = upperCase(trim(jiraProjectKey), ROOT);
        configurationItem = trimToNull(configurationItem);
        assignmentGroup = trimToNull(assignmentGroup);
        category = trimToNull(category);
        riskAssessment = trimToNull(riskAssessment);
        approvers = clean(approvers);
        description = trimToNull(description);
        implementationPlan = trimToNull(implementationPlan);
        backoutPlan = trimToNull(backoutPlan);
        testPlan = trimToNull(testPlan);
    }

    public static final int GROUP_MAX = 200;
    public static final int TEXT_MAX = 2000;

    public static ChangeTemplate suggestedFor(String code, String name, String ownerTeam, String description) {
        return builder().jiraProjectKey(jiraKeyOf(code)).configurationItem(name)
                .assignmentGroup(abbreviateBytes(defaultIfBlank(trim(ownerTeam), name + " Support"), GROUP_MAX))
                .type(NORMAL).category("Software").risk(LOW).impact(Impact.LOW)
                .description(abbreviateBytes(description, TEXT_MAX)).implementationPlan(IMPLEMENTATION_PLAN)
                .backoutPlan(BACKOUT_PLAN).testPlan(TEST_PLAN).build();
    }

    public ChangeTemplate assessed(Risk risk, Impact impact, String riskAssessment, List<String> approvers) {
        return toBuilder().risk(risk).impact(impact).riskAssessment(riskAssessment).approvers(approvers).build();
    }

    static String jiraKeyOf(String code) {
        String letters = defaultString(code).toUpperCase(ROOT).replaceAll("[^A-Z0-9]", "");
        return letters.length() <= 6 ? letters : letters.substring(0, 4);
    }
}

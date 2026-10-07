package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.List;
import java.util.Locale;

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
        jiraProjectKey = jiraProjectKey == null ? null : jiraProjectKey.trim().toUpperCase(Locale.ROOT);
        configurationItem = Text.trimToNull(configurationItem);
        assignmentGroup = Text.trimToNull(assignmentGroup);
        category = Text.trimToNull(category);
        riskAssessment = Text.trimToNull(riskAssessment);
        approvers = Text.clean(approvers);
        description = Text.trimToNull(description);
        implementationPlan = Text.trimToNull(implementationPlan);
        backoutPlan = Text.trimToNull(backoutPlan);
        testPlan = Text.trimToNull(testPlan);
    }

    public static ChangeTemplate suggestedFor(String code, String name, String ownerTeam, String description) {
        return new ChangeTemplate(jiraKeyOf(code), name, Text.orDefault(ownerTeam, name + " Support"), Type.NORMAL,
                "Software", Risk.LOW, Impact.LOW, null, List.of(), description, IMPLEMENTATION_PLAN, BACKOUT_PLAN,
                TEST_PLAN);
    }

    public ChangeTemplate assessed(Risk risk, Impact impact, String riskAssessment, List<String> approvers) {
        return new ChangeTemplate(jiraProjectKey, configurationItem, assignmentGroup, type, category, risk, impact,
                riskAssessment, approvers, description, implementationPlan, backoutPlan, testPlan);
    }

    static String jiraKeyOf(String code) {
        String letters = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return letters.length() <= 6 ? letters : letters.substring(0, 4);
    }
}

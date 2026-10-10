package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.function.Function;

import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BACKOUT_TESTING;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_APPLICATIONS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_USERS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_WORKGROUPS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BUSINESS_IMPACT;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.CHANGE_COMPLEXITY;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.CLIENTS_OUTSIDE_BBH;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.PLATFORM_STATUS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.VALIDATION_COMPLEXITY;
import static java.util.Arrays.stream;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record RiskAssessment(String bbhWorkgroups, String changeComplexity, String bbhUsers,
                             String validationComplexity, String bbhApplications, String backoutTesting,
                             String clientsOutsideBbh, String platformStatus, String businessImpact) {

    public static final RiskAssessment DEFAULTS = builder().build();
    public static final String LOW = "Low";
    public static final String MODERATE = "Moderate";
    public static final String HIGH = "High";

    public RiskAssessment {
        bbhWorkgroups = answered(bbhWorkgroups, BBH_WORKGROUPS);
        changeComplexity = answered(changeComplexity, CHANGE_COMPLEXITY);
        bbhUsers = answered(bbhUsers, BBH_USERS);
        validationComplexity = answered(validationComplexity, VALIDATION_COMPLEXITY);
        bbhApplications = answered(bbhApplications, BBH_APPLICATIONS);
        backoutTesting = answered(backoutTesting, BACKOUT_TESTING);
        clientsOutsideBbh = answered(clientsOutsideBbh, CLIENTS_OUTSIDE_BBH);
        platformStatus = answered(platformStatus, PLATFORM_STATUS);
        businessImpact = answered(businessImpact, BUSINESS_IMPACT);
    }

    private static String answered(String answer, Question question) {
        return getIfNull(trimToNull(answer), question.options().get(0));
    }

    String risk() {
        if (stream(Question.values()).anyMatch(question -> question.rankIn(this) == question.options().size() - 1)) {
            return HIGH;
        }
        return stream(Question.values()).anyMatch(question -> question.rankIn(this) > 0) ? MODERATE : LOW;
    }

    void validate(ValidationProblems problems) {
        for (Question question : Question.values()) {
            if (question.rankIn(this) < 0) {
                problems.add(question.field(), "must be one of " + String.join(", ", question.options()));
            }
        }
    }

    List<String> lines() {
        return stream(Question.values()).map(question -> question.label() + ": " + question.answerIn(this)).toList();
    }

    @RequiredArgsConstructor
    public enum Question {
        BBH_WORKGROUPS("bbhWorkgroups", "Number of BBH workgroups impacted",
                List.of("Single", "2-3", "More than 3"), RiskAssessment::bbhWorkgroups),
        CHANGE_COMPLEXITY("changeComplexity", "Complexity of the change",
                List.of("Simple", "Moderate", "Very"), RiskAssessment::changeComplexity),
        BBH_USERS("bbhUsers", "Number of BBH users impacted",
                List.of("Less than 5", "5-25", "26-250", "All users"), RiskAssessment::bbhUsers),
        VALIDATION_COMPLEXITY("validationComplexity", "Complexity of validation",
                List.of("Simple", "Moderate", "Very"), RiskAssessment::validationComplexity),
        BBH_APPLICATIONS("bbhApplications", "Number of applications impacted",
                List.of("Single", "Two", "More than 2"), RiskAssessment::bbhApplications),
        BACKOUT_TESTING("backoutTesting", "Backout testing & duration",
                List.of("Less than 30 minutes", "30 mins - 2 hours", "Greater than 2 hours", "Unable to test"),
                RiskAssessment::backoutTesting),
        CLIENTS_OUTSIDE_BBH("clientsOutsideBbh", "Number of impacted clients outside BBH",
                List.of("No clients", "Single", "More than one but not all", "All clients"),
                RiskAssessment::clientsOutsideBbh),
        PLATFORM_STATUS("platformStatus", "Platform status",
                List.of("Existing", "New", "Decommissioned"), RiskAssessment::platformStatus),
        BUSINESS_IMPACT("businessImpact", "Business impact",
                List.of("None", "Low", "Medium", "High"), RiskAssessment::businessImpact);

        @Getter
        private final String field;
        @Getter
        private final String label;
        @Getter
        private final List<String> options;
        private final Function<RiskAssessment, String> answer;

        public String answerIn(RiskAssessment assessment) {
            return answer.apply(assessment);
        }

        int rankIn(RiskAssessment assessment) {
            return options.indexOf(answerIn(assessment));
        }
    }
}

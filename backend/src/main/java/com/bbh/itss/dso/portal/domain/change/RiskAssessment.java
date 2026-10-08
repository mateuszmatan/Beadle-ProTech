package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder(toBuilder = true)
public record RiskAssessment(String bbhWorkgroups, String changeComplexity, String bbhUsers,
                             String validationComplexity, String bbhApplications, String backoutTesting,
                             String clientsOutsideBbh, String platformStatus, String businessImpact) {

    public static final RiskAssessment NONE = builder().build();
    public static final String LOW = "Low";
    public static final String MODERATE = "Moderate";
    public static final String HIGH = "High";

    public RiskAssessment {
        bbhWorkgroups = trimToNull(bbhWorkgroups);
        changeComplexity = trimToNull(changeComplexity);
        bbhUsers = trimToNull(bbhUsers);
        validationComplexity = trimToNull(validationComplexity);
        bbhApplications = trimToNull(bbhApplications);
        backoutTesting = trimToNull(backoutTesting);
        clientsOutsideBbh = trimToNull(clientsOutsideBbh);
        platformStatus = trimToNull(platformStatus);
        businessImpact = trimToNull(businessImpact);
    }

    String risk() {
        List<Question> answered = stream(Question.values()).filter(question -> question.answerIn(this) != null)
                .toList();
        if (answered.isEmpty()) {
            return null;
        }
        if (answered.stream().anyMatch(question -> question.rankIn(this) == question.options().size() - 1)) {
            return HIGH;
        }
        return answered.stream().anyMatch(question -> question.rankIn(this) > 0) ? MODERATE : LOW;
    }

    void validate(ValidationProblems problems) {
        for (Question question : Question.values()) {
            if (question.answerIn(this) != null && question.rankIn(this) < 0) {
                problems.add(question.field(), "must be one of " + String.join(", ", question.options()));
            }
        }
    }

    List<String> lines() {
        return stream(Question.values()).filter(question -> question.answerIn(this) != null)
                .map(question -> question.label() + ": " + question.answerIn(this)).toList();
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

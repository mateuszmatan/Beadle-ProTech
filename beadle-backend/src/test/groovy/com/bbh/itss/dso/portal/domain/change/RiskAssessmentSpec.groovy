package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ChangeFixtures.risk
import static com.bbh.itss.dso.portal.support.CatalogFixtures.copy

class RiskAssessmentSpec extends Specification {

    def "an assessment of #answers is #expected"() {
        expect:
        copy(answers, RiskAssessment.DEFAULTS).risk() == expected

        where:
        answers                                                                    || expected
        [:]                                                                        || 'Low'
        [bbhWorkgroups: ' ', businessImpact: '']                                   || 'Low'
        [bbhWorkgroups: 'Single', changeComplexity: 'Simple']                      || 'Low'
        [bbhUsers: 'Less than 5', clientsOutsideBbh: 'No clients', businessImpact: 'None'] || 'Low'
        [bbhWorkgroups: 'Single', bbhUsers: '5-25']                                || 'Moderate'
        [businessImpact: 'Medium']                                                 || 'Moderate'
        [backoutTesting: 'Greater than 2 hours', platformStatus: 'New']            || 'Moderate'
        [bbhWorkgroups: 'Single', bbhApplications: 'More than 2']                  || 'High'
        [bbhUsers: 'All users']                                                    || 'High'
        [backoutTesting: 'Unable to test']                                         || 'High'
        [clientsOutsideBbh: 'All clients', changeComplexity: 'Simple']             || 'High'
        [platformStatus: 'Decommissioned']                                         || 'High'
        [validationComplexity: 'Very']                                             || 'High'
        [businessImpact: 'High']                                                   || 'High'
        [bbhWorkgroups: 'More than 3']                                             || 'High'
        [bbhWorkgroups: 'Five', businessImpact: 'Low']                             || 'Moderate'
    }

    def "every question rates High on its last option, Moderate past its first and Low on its first"() {
        expect:
        Question.values().every { question ->
            def options = question.options()
            [options.first(), options[1], options.last()].collect { answer(question, it).risk() } ==
                    ['Low', 'Moderate', 'High']
        }
    }

    def "the questions offer the options of the ProTech form"() {
        expect:
        Question.values().collectEntries { [it.field(), it.options()] } == [
                bbhWorkgroups       : ['Single', '2-3', 'More than 3'],
                changeComplexity    : ['Simple', 'Moderate', 'Very'],
                bbhUsers            : ['Less than 5', '5-25', '26-250', 'All users'],
                validationComplexity: ['Simple', 'Moderate', 'Very'],
                bbhApplications     : ['Single', 'Two', 'More than 2'],
                backoutTesting      : ['Less than 30 minutes', '30 mins - 2 hours', 'Greater than 2 hours',
                                       'Unable to test'],
                clientsOutsideBbh   : ['No clients', 'Single', 'More than one but not all', 'All clients'],
                platformStatus      : ['Existing', 'New', 'Decommissioned'],
                businessImpact      : ['None', 'Low', 'Medium', 'High']]
    }

    def "an assessment trims its answers, takes the first option of a question left out and accepts only listed options"() {
        given:
        def problems = new ValidationProblems()

        when:
        risk(bbhUsers: ' 26-250 ', backoutTesting: 'unable to test', platformStatus: null).validate(problems)

        then:
        problems.list().collect { "$it.field $it.message".toString() } == ['backoutTesting must be one of' +
                ' Less than 30 minutes, 30 mins - 2 hours, Greater than 2 hours, Unable to test']
        risk(bbhUsers: ' 26-250 ').bbhUsers() == '26-250'
        RiskAssessment.builder().bbhUsers('All users').businessImpact('Low').build().lines() ==
                ['Number of BBH workgroups impacted: Single', 'Complexity of the change: Simple',
                 'Number of BBH users impacted: All users', 'Complexity of validation: Simple',
                 'Number of applications impacted: Single', 'Backout testing & duration: Less than 30 minutes',
                 'Number of impacted clients outside BBH: No clients', 'Platform status: Existing',
                 'Business impact: Low']
        RiskAssessment.DEFAULTS == new RiskAssessment('Single', 'Simple', 'Less than 5', 'Simple', 'Single',
                'Less than 30 minutes', 'No clients', 'Existing', 'None')
        risk(platformStatus: ' ').platformStatus() == 'Existing'
    }

    private static RiskAssessment answer(Question question, String option) {
        copy([(question.field()): option], RiskAssessment.DEFAULTS)
    }
}

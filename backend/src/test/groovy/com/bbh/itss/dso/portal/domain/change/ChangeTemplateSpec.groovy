package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.support.ChangeFixtures.template

class ChangeTemplateSpec extends Specification {

    def "a template trims its texts, upper-cases the Jira key and keeps each approver once"() {
        when:
        def trimmed = new ChangeTemplate(' cert ', ' CertScanner ', ' TA ', ChangeTemplate.Type.NORMAL, ' ',
                ChangeTemplate.Risk.LOW, ChangeTemplate.Impact.LOW, ' ', [' Ann ', 'Ann', ' ', 'Bob'], '', ' a ',
                null, ' c ')

        then:
        trimmed == new ChangeTemplate('CERT', 'CertScanner', 'TA', ChangeTemplate.Type.NORMAL, null,
                ChangeTemplate.Risk.LOW, ChangeTemplate.Impact.LOW, null, ['Ann', 'Bob'], null, 'a', null, 'c')
        new ChangeTemplate(null, null, null, null, null, null, null, null, null, null, null, null, null)
                .approvers() == []
    }

    def "the template suggested for #code takes the Jira key #key, the product name and its owner team"() {
        when:
        def suggested = ChangeTemplate.suggestedFor(code, 'Product', owner, 'About it')

        then:
        suggested.jiraProjectKey() == key
        suggested.configurationItem() == 'Product'
        suggested.assignmentGroup() == group
        [suggested.type(), suggested.category(), suggested.risk(), suggested.impact()] ==
                [ChangeTemplate.Type.NORMAL, 'Software', ChangeTemplate.Risk.LOW, ChangeTemplate.Impact.LOW]
        [suggested.riskAssessment(), suggested.approvers(), suggested.description()] == [null, [], 'About it']
        [suggested.implementationPlan(), suggested.backoutPlan(), suggested.testPlan()] ==
                [ChangeTemplate.IMPLEMENTATION_PLAN, ChangeTemplate.BACKOUT_PLAN, ChangeTemplate.TEST_PLAN]

        where:
        code          | owner        || key      | group
        'CERTSCANNER' | 'TA'         || 'CERT'   | 'TA'
        'PAYHUB'      | null         || 'PAYHUB' | 'Product Support'
        'fx-rates_2'  | ' '          || 'FXRA'   | 'Product Support'
    }

    def "an assessed template changes only its risk, impact, assessment and approvers"() {
        expect:
        template().assessed(ChangeTemplate.Risk.HIGH, ChangeTemplate.Impact.HIGH, 'Big', ['Zoe']) ==
                template(risk: ChangeTemplate.Risk.HIGH, impact: ChangeTemplate.Impact.HIGH, riskAssessment: 'Big',
                        approvers: ['Zoe'])
    }

    def "a profile is created at version 0 and changed only at the version it was read at"() {
        given:
        def stored = new ChangeProfile(4L, template(), 2, Instant.EPOCH)

        expect:
        ChangeProfile.create(4L, template()) == new ChangeProfile(4L, template(), 0, null)
        stored.change(2L, template(category: 'Apps')) == new ChangeProfile(4L, template(category: 'Apps'), 2, Instant.EPOCH)

        when:
        stored.change(version, template())

        then:
        thrown(ConflictException)

        when:
        new ChangeProfile(4L, null, 0, null)

        then:
        thrown(NullPointerException)

        where:
        version << [1L, null]
    }

    def "the suggested template fits the columns of a long description and a long product name"() {
        when:
        def suggested = ChangeTemplate.suggestedFor('LONG', 'N' * 195, null, 'é' * 2500)

        then:
        suggested.assignmentGroup().getBytes('UTF-8').length <= ChangeTemplate.GROUP_MAX
        suggested.assignmentGroup().endsWith('...')
        suggested.description().getBytes('UTF-8').length <= ChangeTemplate.TEXT_MAX
        suggested.description().endsWith('...')
    }

    def "a date range is refused when #problem"() {
        when:
        new DateRange(from, to)

        then:
        def e = thrown(InvalidRequestException)
        e.problems*.field == [field]
        e.message == message

        where:
        problem                  | from                | to                  || field  | message
        'the start is missing'   | null                | day('2026-10-01')   || 'from' | 'choose a date'
        'the end is missing'     | day('2026-10-01')   | null                || 'to'   | 'choose a date'
        'it ends before it starts' | day('2026-10-02') | day('2026-10-01')   || 'to'   | 'must not be before the start date 2026-10-02'
        'it spans over a year'   | day('2025-01-01')   | day('2026-01-03')   || 'to'   | 'the range may span at most 366 days'
    }

    def "a date range holds its first and last day"() {
        given:
        def range = new DateRange(day('2026-09-01'), day('2026-09-30'))

        expect:
        ['2026-09-01', '2026-09-15', '2026-09-30'].every { range.contains(day(it)) }
        !range.contains(day('2026-08-31'))
        !range.contains(day('2026-10-01'))
    }

    def "a change window is checked against the clock: #expected"() {
        given:
        def problems = new ValidationProblems()

        when:
        new ChangeWindow(start, end).check(Instant.parse('2026-10-07T12:00:00Z'), problems)

        then:
        problems.list().collect { "$it.field $it.message".toString() } == expected

        where:
        start                         | end                           || expected
        at('2026-10-10T06:00:00Z')    | at('2026-10-10T10:00:00Z')    || []
        null                          | null                          || ['start choose when the change starts', 'end choose when the change ends']
        at('2026-10-07T12:00:00Z')    | at('2026-10-07T13:00:00Z')    || ['start must be in the future']
        at('2026-10-10T06:00:00Z')    | at('2026-10-10T06:00:00Z')    || ['end must be after the start']
        at('2026-10-10T06:00:00Z')    | at('2026-10-17T06:00:01Z')    || ['end a change window may last at most 7 days']
    }

    def "a change window reads as #text"() {
        expect:
        new ChangeWindow(at(start), at(end)).text() == text

        where:
        start                  | end                    || text
        '2026-10-10T06:00:00Z' | '2026-10-10T10:00:00Z' || '2026-10-10 06:00 to 10:00 UTC'
        '2026-10-10T22:00:00Z' | '2026-10-11T02:30:00Z' || '2026-10-10 22:00 to 2026-10-11 02:30 UTC'
    }

    def "a Jira issue reads as its key, summary and status"() {
        expect:
        new JiraIssue('CERT-1', 'Alerts', 'Done', null, day('2026-10-01')).line() == 'CERT-1 Alerts (Done)'
        new JiraIssue('CERT-2', 'Mails', null, 'CERT-1', day('2026-10-01')).line() == 'CERT-2 Mails'
    }

    private static LocalDate day(String text) {
        LocalDate.parse(text)
    }

    private static Instant at(String text) {
        Instant.parse(text)
    }
}

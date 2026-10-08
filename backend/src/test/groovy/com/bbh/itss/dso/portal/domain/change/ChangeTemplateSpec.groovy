package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.BACKOUT_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.FIRST_USE_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.IMPLEMENTATION_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEST_SUMMARY
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEXT_MAX
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.STANDARD
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.VALIDATION_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor
import static com.bbh.itss.dso.portal.domain.change.JiraVersion.UNRELEASED_NEWEST_FIRST
import static com.bbh.itss.dso.portal.support.ChangeFixtures.at
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.risk
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static java.time.Instant.EPOCH

class ChangeTemplateSpec extends Specification {

    def "a template trims its texts to null, upper-cases the Jira key and fills in the optional sections"() {
        when:
        def trimmed = new ChangeTemplate(' cert ', ' TA ', ' ', NORMAL, ' CertScanner ', ' ',
                ' INC0012345 ', '', ' Clients ', ' About ', new Approvers(' Ann ', ' ', null), null,
                new Timing(' 18:00 ', 2, 1), new Planning(' t ', 'i', ' ', 'b', 'f'),
                new PrivilegedAccess(null, [new PrivilegedUser(' Jane ', ' adm_jane '), null]),
                RiskAssessment.builder().bbhWorkgroups(1).businessImpact(' Low ').changeComplexity(' ').build())
        def empty = ChangeTemplate.builder().build()

        then:
        trimmed == new ChangeTemplate('CERT', 'TA', null, NORMAL, 'CertScanner', null,
                'INC0012345', null, 'Clients', 'About', new Approvers('Ann', null, null), false,
                new Timing('18:00', 2, 1), new Planning('t', 'i', null, 'b', 'f'),
                new PrivilegedAccess(false, [new PrivilegedUser('Jane', 'adm_jane'), null]),
                RiskAssessment.builder().bbhWorkgroups(1).businessImpact('Low').build())
        [empty.jiraProjectKey(), empty.timing(), empty.planning()] == [null, null, null]
        [empty.approvers(), empty.downtime(), empty.privilegedAccess(), empty.riskAssessment()] ==
                [Approvers.NONE, false, PrivilegedAccess.NONE, RiskAssessment.NONE]
    }

    def "the template suggested for #code takes the Jira key #key, the product name and its owner team"() {
        when:
        def suggested = suggestedFor(code, 'Product', owner, 'About it')

        then:
        suggested == new ChangeTemplate(key, group, 'Software', NORMAL, 'Product', null, null,
                null, null, 'About it', Approvers.NONE, false, new Timing('18:00', 2, 1), Planning.SUGGESTED,
                new PrivilegedAccess(false, []), RiskAssessment.NONE)
        Planning.SUGGESTED == new Planning(TEST_SUMMARY, IMPLEMENTATION_PLAN, VALIDATION_PLAN, BACKOUT_PLAN,
                FIRST_USE_PLAN)
        VALIDATION_PLAN == 'Run the smoke tests of the DevSecOps pipeline against production and' +
                ' check the monitoring of each service.'
        FIRST_USE_PLAN == 'The business owner confirms the first use of the release in production.'
        problems(suggested) == []

        where:
        code          | owner || key      | group
        'CERTSCANNER' | 'TA'  || 'CERT'   | 'TA'
        'PAYHUB'      | null  || 'PAYHUB' | 'Product Support'
        'fx-rates_2'  | ' '   || 'FXRA'   | 'Product Support'
    }

    def "the suggested template fits the columns of a long description and a long product name"() {
        when:
        def suggested = suggestedFor('LONG', 'N' * 195, null, 'é' * 2500)

        then:
        suggested.assignmentGroup().getBytes('UTF-8').length <= GROUP_MAX
        suggested.assignmentGroup().endsWith('...')
        suggested.description().getBytes('UTF-8').length <= TEXT_MAX
        suggested.description().endsWith('...')
    }

    def "a raised template takes the FixVersion as its release unless it names one"() {
        expect:
        template().releasedAs('CERT 4.2') == template(release: 'CERT 4.2')
        template(release: 'R42').releasedAs('CERT 4.2') == template(release: 'R42')
    }

    def "a complete template has no problems, also with privileged access for up to seven users"() {
        expect:
        problems(template()) == []
        problems(template(privilegedAccess: privileged(1))) == []
        problems(template(privilegedAccess: privileged(7))) == []
        problems(template(approvers: null, riskAssessment: null)) == []
    }

    def "a template is refused when #problem"() {
        expect:
        problems(template(edits)) == expected

        where:
        problem                                  | edits                                                       || expected
        'it misses its required texts'           | [jiraProjectKey: ' ', assignmentGroup: null, category: '', type: null, configurationItem: ' '] || ['template.jiraProjectKey is required', 'template.assignmentGroup is required', 'template.category is required', 'template.type is required', 'template.configurationItem is required']
        'the Jira key has a dash'                | [jiraProjectKey: 'CE-RT']                                   || ['template.jiraProjectKey ' + JIRA_KEY_MESSAGE]
        'it has no timing and no planning'       | [timing: null, planning: null]                              || ['template.timing is required', 'template.planning is required']
        'a planning text is missing'             | [planning: new Planning('t', ' ', 'v', null, 'f')]          || ['template.planning.implementationPlan is required', 'template.planning.backoutPlan is required']
        'the installation starts at no time'     | [timing: new Timing('6pm', 2, 1)]                           || ['template.timing.installationStart must be a time of day such as 18:00']
        'the timing is missing'                  | [timing: new Timing(null, null, null)]                      || ['template.timing.installationStart is required', 'template.timing.installationHours is required', 'template.timing.validationHours is required']
        'the installation takes no time'         | [timing: new Timing('24:00', 0, 0)]                         || ['template.timing.installationStart must be a time of day such as 18:00', 'template.timing.installationHours must be between 1 and 72 hours']
        'the hours exceed three days'            | [timing: new Timing('23:59', 73, 73)]                       || ['template.timing.installationHours must be between 1 and 72 hours', 'template.timing.validationHours must be between 0 and 72 hours']
        'the validation hours are negative'      | [timing: new Timing('00:00', 72, -1)]                       || ['template.timing.validationHours must be between 0 and 72 hours']
        'privileged access names no user'        | [privilegedAccess: new PrivilegedAccess(true, [])]          || ['template.privilegedAccess.users add the users who need privileged access']
        'users are named without privileged access' | [privilegedAccess: new PrivilegedAccess(false, privileged(1).users())] || ['template.privilegedAccess.users must be empty when the change needs no privileged access']
        'eight users need privileged access'     | [privilegedAccess: privileged(8)]                           || ['template.privilegedAccess.users may list at most 7 users']
        'a privileged user is incomplete'        | [privilegedAccess: new PrivilegedAccess(true, [new PrivilegedUser('Ann', 'adm_ann'), null, new PrivilegedUser(' ', null)])] || ['template.privilegedAccess.users[1] is required', 'template.privilegedAccess.users[2].user is required', 'template.privilegedAccess.users[2].account is required']
        'the risk numbers are negative'          | [riskAssessment: risk(bbhWorkgroups: -1, bbhUsers: -2, bbhApplications: -3, clients: -4, clientsOutsideBbh: -5)] || ['bbhWorkgroups', 'bbhUsers', 'bbhApplications', 'clients', 'clientsOutsideBbh'].collect { "template.riskAssessment.$it must not be negative".toString() }
        'its texts fit in characters but not in bytes' | [assignmentGroup: 'ł' * 101, incident: 'é' * 21, affectedClients: 'ą' * 1001, approvers: new Approvers(null, 'Zoë', 'ż' * 101), planning: new Planning('t', 'i', 'ś' * 1001, 'b', 'f'), privilegedAccess: new PrivilegedAccess(true, [new PrivilegedUser('Ann', 'ü' * 101)]), riskAssessment: risk(businessImpact: 'ö' * 51, backoutTesting: 'ę' * 1001)] || ['template.assignmentGroup is too long: it may take at most 200 bytes', 'template.incident is too long: it may take at most 40 bytes', 'template.affectedClients is too long: it may take at most 2000 bytes', 'template.approvers.businessApprover is too long: it may take at most 200 bytes', 'template.planning.validationPlan is too long: it may take at most 2000 bytes', 'template.privilegedAccess.users[0].account is too long: it may take at most 200 bytes', 'template.riskAssessment.businessImpact is too long: it may take at most 100 bytes', 'template.riskAssessment.backoutTesting is too long: it may take at most 2000 bytes']
        'its texts fill their columns'           | [assignmentGroup: 'ł' * 100, incident: 'é' * 20, approvers: new Approvers('ż' * 100, 'ż' * 100, 'ż' * 100), riskAssessment: risk(businessImpact: 'ö' * 50)] || []
    }

    def "a profile is created at version 0 with its tasks and changed only at the version it was read at"() {
        given:
        def stored = new ChangeProfile(4L, template(), tasks(), 2, EPOCH)

        expect:
        ChangeProfile.create(4L, template(), tasks(1)) == new ChangeProfile(4L, template(), tasks(1), 0, null)
        stored.change(2L, template(category: 'Apps'), tasks(3)) ==
                new ChangeProfile(4L, template(category: 'Apps'), tasks(3), 2, EPOCH)

        when:
        stored.change(version, template(), tasks())

        then:
        thrown(IllegalStateException)

        when:
        new ChangeProfile(4L, null, tasks(), 0, null)

        then:
        thrown(NullPointerException)

        where:
        version << [1L, null]
    }

    def "an edit of a raised template keeps its Jira project, its type and its timing"() {
        given:
        def raised = template(release: 'CERT 4.2')
        def edits = template(jiraProjectKey: 'OTHER', type: STANDARD, timing: new Timing('20:00', 5, 5),
                category: 'Apps', release: 'CERT 4.3', downtime: true)

        expect:
        raised.edited(edits) == template(category: 'Apps', release: 'CERT 4.3', downtime: true)
    }

    def "a schedule is checked for presence and order: #expected"() {
        given:
        def problems = new ValidationProblems()

        when:
        schedule(edits).check(problems.at('schedule'))

        then:
        problems.list().collect { "$it.field $it.message".toString() } == expected

        where:
        edits                                                                        || expected
        [:]                                                                          || []
        [validationStart: '2026-10-10T10:00:00Z', validationEnd: '2026-10-10T10:00:00Z', firstUsage: '2026-10-10T10:00:00Z'] || []
        [installationStart: null, installationEnd: null, validationStart: null, validationEnd: null, firstUsage: null] || ['schedule.installationStart choose when the installation starts', 'schedule.installationEnd choose when the installation ends', 'schedule.validationStart choose when the post-install validation starts', 'schedule.validationEnd choose when the post-install validation ends', 'schedule.firstUsage choose when the release is first used']
        [installationEnd: '2026-10-10T06:00:00Z', validationStart: '2026-10-10T06:00:00Z'] || ['schedule.installationEnd must be after the installation start']
        [validationStart: '2026-10-10T09:59:00Z']                                    || ['schedule.validationStart must not be before the installation end']
        [validationEnd: '2026-10-10T09:59:00Z']                                      || ['schedule.validationEnd must not be before the validation start']
        [firstUsage: '2026-10-10T10:59:00Z']                                         || ['schedule.firstUsage must not be before the validation end']
        [installationStart: null, validationEnd: null]                               || ['schedule.installationStart choose when the installation starts', 'schedule.validationEnd choose when the post-install validation ends']
    }

    def "only a schedule that starts after #now is upcoming"() {
        given:
        def problems = new ValidationProblems()

        when:
        schedule().checkUpcoming(at(now), problems)

        then:
        problems.list()*.field == fields

        where:
        now                    || fields
        '2026-10-10T05:59:59Z' || []
        '2026-10-10T06:00:00Z' || ['installationStart']
        '2026-10-11T00:00:00Z' || ['installationStart']
    }

    def "a schedule reads as its installation, validation and first usage"() {
        expect:
        schedule().text() == 'Installation 2026-10-10 06:00 to 10:00 UTC, post-install validation 2026-10-10 10:00' +
                ' to 11:00 UTC, first usage 2026-10-12 08:00 UTC'
        schedule(installationStart: '2026-10-10T22:00:00Z', installationEnd: '2026-10-11T02:30:00Z')
                .installationText() == '2026-10-10 22:00 to 2026-10-11 02:30 UTC'
    }

    def "a Jira issue reads as its key, summary and status"() {
        expect:
        new JiraIssue('CERT-1', 'Alerts', 'Done', null, LocalDate.parse('2026-10-01')).line() == 'CERT-1 Alerts (Done)'
        new JiraIssue('CERT-2', 'Mails', null, 'CERT-1', LocalDate.parse('2026-10-01')).line() == 'CERT-2 Mails'
    }

    def "FixVersions are ordered unreleased first, then released, each newest first"() {
        given:
        def versions = [new JiraVersion('CERT 4.0', true, LocalDate.parse('2026-07-01')),
                        new JiraVersion('CERT 4.2', false, LocalDate.parse('2026-10-20')),
                        new JiraVersion('CERT 4.1', true, LocalDate.parse('2026-09-01')),
                        new JiraVersion('CERT 4.3', false, null),
                        new JiraVersion('CERT 3.9', true, null)]

        expect:
        versions.sort(false, UNRELEASED_NEWEST_FIRST)*.name() ==
                ['CERT 4.3', 'CERT 4.2', 'CERT 3.9', 'CERT 4.1', 'CERT 4.0']
    }

    private static List<String> problems(ChangeTemplate template) {
        def problems = new ValidationProblems()
        template.validate(problems.at('template'))
        problems.list().collect { "$it.field $it.message".toString() }
    }
}

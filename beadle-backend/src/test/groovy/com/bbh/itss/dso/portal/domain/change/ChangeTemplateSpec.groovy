package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
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
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.EMERGENCY
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
        def trimmed = ChangeTemplate.builder().jiraProjectKey(' cert ').requestedFor(' Ann Lee ').requestedBy(' ')
                .department(' Custody ').assignmentGroup(' TA ').category(' Application ').assignedTo(' Jane Smith ')
                .type(STANDARD).release(' ').configurationItem(' CertScanner ').incident(' INC0012345 ')
                .directBusinessService(' Certificates ').problem('').affectedClients(' Clients ')
                .usersAffected(' Operators ').approvers(new Approvers(' Ann ', ' ', null))
                .timing(new Timing(' 18:00 ', 2, 1)).planning(new Planning(' t ', 'i', ' ', 'b', 'f'))
                .privilegedAccess(new PrivilegedAccess(null, [new PrivilegedUser(' Jane ', ' adm_jane '), null]))
                .riskAssessment(RiskAssessment.builder().bbhWorkgroups(' 2-3 ').businessImpact(' Low ')
                        .changeComplexity(' ').build())
                .secureCodingTicket(' SCP-1 ').secureCoding(new SecureCoding(' APO-1 ', ' ', null, ' https://qc ')).build()
        def empty = ChangeTemplate.builder().build()

        then:
        trimmed == ChangeTemplate.builder().jiraProjectKey('CERT').requestedFor('Ann Lee').department('Custody')
                .assignmentGroup('TA').category('Application').assignedTo('Jane Smith').type(STANDARD)
                .configurationItem('CertScanner').incident('INC0012345').directBusinessService('Certificates')
                .affectedClients('Clients').usersAffected('Operators')
                .approvers(new Approvers('Ann', null, null)).downtime(false).timing(new Timing('18:00', 2, 1))
                .planning(new Planning('t', 'i', null, 'b', 'f'))
                .privilegedAccess(new PrivilegedAccess(false, [new PrivilegedUser('Jane', 'adm_jane'), null]))
                .riskAssessment(RiskAssessment.builder().bbhWorkgroups('2-3').businessImpact('Low').build())
                .secureCodingTicket('SCP-1').secureCoding(new SecureCoding('APO-1', null, null, 'https://qc')).build()
        trimmed.risk() == 'Moderate'
        [empty.jiraProjectKey(), empty.timing(), empty.planning(), empty.risk()] == [null, null, null, 'Low']
        [empty.approvers(), empty.downtime(), empty.privilegedAccess(), empty.riskAssessment(), empty.secureCoding()] ==
                [Approvers.NONE, false, PrivilegedAccess.NONE, RiskAssessment.DEFAULTS, SecureCoding.NONE]
    }

    def "a template computes its risk from the assessment and ignores the risk it is given"() {
        expect:
        template(risk: 'High').risk() == 'Moderate'
        template(riskAssessment: null, risk: 'High').risk() == 'Low'
    }

    def "the template suggested for #code takes the Jira key #key, the product name and its owner team"() {
        when:
        def suggested = suggestedFor(code, 'Product', owner)

        then:
        suggested == ChangeTemplate.builder().jiraProjectKey(key).assignmentGroup(group).category('Application')
                .type(STANDARD).configurationItem('Product')
                .timing(new Timing('18:00', 2, 1)).planning(Planning.SUGGESTED).build()
        suggested.risk() == 'Low'
        Planning.SUGGESTED == new Planning(TEST_SUMMARY, IMPLEMENTATION_PLAN, VALIDATION_PLAN, BACKOUT_PLAN,
                FIRST_USE_PLAN)
        VALIDATION_PLAN == 'Run the smoke tests of the DevSecOps pipeline against production and' +
                ' check the monitoring of the product.'
        FIRST_USE_PLAN == 'The business owner confirms the first use of the release in production.'
        problems(suggested) == []

        where:
        code          | owner || key      | group
        'CERTSCANNER' | 'TA'  || 'CERT'   | 'TA'
        'PAYHUB'      | null  || 'PAYHUB' | 'Product Support'
        'fx-rates_2'  | ' '   || 'FXRA'   | 'Product Support'
    }

    def "the suggested template fits the columns of a long product name"() {
        when:
        def suggested = suggestedFor('LONG', 'N' * 195, null)
        def multibyte = suggestedFor('LONG', 'Ł' * 200, null)

        then:
        suggested.assignmentGroup().getBytes('UTF-8').length <= GROUP_MAX
        suggested.assignmentGroup().endsWith('...')
        suggested.configurationItem() == 'N' * 195
        multibyte.configurationItem().getBytes('UTF-8').length <= GROUP_MAX
        multibyte.configurationItem().endsWith('...')
        problems(multibyte) == []
    }

    def "a raised template takes the FixVersion as its release unless it names one"() {
        expect:
        template().releasedAs('CERT 4.2') == template(release: 'CERT 4.2')
        template(release: 'R42').releasedAs('CERT 4.2') == template(release: 'R42')
    }

    def "a raised template names the signed-in user for its empty people and the product's department"() {
        expect:
        template().openedBy('Mateusz Matan', 'Custody') == template(requestedFor: 'Mateusz Matan',
                requestedBy: 'Mateusz Matan', department: 'Custody', assignedTo: 'Mateusz Matan')
        template(requestedFor: 'Ann Lee', requestedBy: 'Jane Smith', department: 'AI Lab', assignedTo: 'Grace Turner')
                .openedBy('Mateusz Matan', 'Custody') == template(requestedFor: 'Ann Lee', requestedBy: 'Jane Smith',
                department: 'AI Lab', assignedTo: 'Grace Turner')
        template().openedBy('Mateusz Matan', null).department() == null
    }

    def "a complete template has no problems, also with privileged access for up to seven users"() {
        expect:
        problems(template()) == []
        problems(template(privilegedAccess: privileged(1))) == []
        problems(template(privilegedAccess: privileged(7))) == []
        problems(template(approvers: null, riskAssessment: null)) == []
        problems(template(secureCoding: new SecureCoding('APO-1', 'https://' + 'b' * 492, 'http://nexus', null))) == []
    }

    def "a template is refused when #problem"() {
        expect:
        problems(template(edits)) == expected

        where:
        problem                                  | edits                                                       || expected
        'it misses its required texts'           | [jiraProjectKey: ' ', assignmentGroup: null, category: '', type: null, configurationItem: ' '] || ['template.jiraProjectKey is required', 'template.assignmentGroup is required', 'template.category is required', 'template.type is required', 'template.configurationItem is required']
        'the Jira key has a dash'                | [jiraProjectKey: 'CE-RT']                                   || ['template.jiraProjectKey ' + JIRA_KEY_MESSAGE]
        'secure coding links are no links'       | [secureCoding: new SecureCoding('APO-1', 'bitbucket.bbh.com/projects/CERT', null, 'ftp://cert')] || ['template.secureCoding.bitbucketUrl ' + SecureCoding.LINK_MESSAGE, 'template.secureCoding.qcApplicationLink ' + SecureCoding.LINK_MESSAGE]
        'secure coding inputs are too long'      | [secureCoding: new SecureCoding('A' * 41, 'https://' + 'b' * 493, null, null)] || ['template.secureCoding.apoNumber is too long: it may take at most 40 bytes', 'template.secureCoding.bitbucketUrl is too long: it may take at most 500 bytes']
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
        'the category is not on the list'        | [category: 'Software']                                      || ['template.category must be one of Application, Hardware, Infrastructure, System Software, Network, Telecom, Data Amendment, Desktop Software, Storage, Facilities, Other, Database']
        'the risk answers are not on their lists' | [riskAssessment: risk(bbhWorkgroups: '1', changeComplexity: 'Low', bbhUsers: '10', validationComplexity: 'simple', bbhApplications: '2', backoutTesting: 'Tested on QC', clientsOutsideBbh: '0', platformStatus: 'Existing platform', businessImpact: 'Minor')] || ['template.riskAssessment.bbhWorkgroups must be one of Single, 2-3, More than 3', 'template.riskAssessment.changeComplexity must be one of Simple, Moderate, Very', 'template.riskAssessment.bbhUsers must be one of Less than 5, 5-25, 26-250, All users', 'template.riskAssessment.validationComplexity must be one of Simple, Moderate, Very', 'template.riskAssessment.bbhApplications must be one of Single, Two, More than 2', 'template.riskAssessment.backoutTesting must be one of Less than 30 minutes, 30 mins - 2 hours, Greater than 2 hours, Unable to test', 'template.riskAssessment.clientsOutsideBbh must be one of No clients, Single, More than one but not all, All clients', 'template.riskAssessment.platformStatus must be one of Existing, New, Decommissioned', 'template.riskAssessment.businessImpact must be one of None, Low, Medium, High']
        'its texts fit in characters but not in bytes' | [requestedFor: 'ł' * 101, requestedBy: 'ł' * 101, department: 'é' * 51, assignmentGroup: 'ł' * 101, assignedTo: 'ł' * 101, incident: 'é' * 21, directBusinessService: 'ł' * 101, affectedClients: 'ą' * 1001, usersAffected: 'ą' * 1001, secureCodingTicket: 'é' * 21, approvers: new Approvers(null, 'Zoë', 'ż' * 101), planning: new Planning('t', 'i', 'ś' * 1001, 'b', 'f'), privilegedAccess: new PrivilegedAccess(true, [new PrivilegedUser('Ann', 'ü' * 101)])] || ['template.requestedFor is too long: it may take at most 200 bytes', 'template.requestedBy is too long: it may take at most 200 bytes', 'template.department is too long: it may take at most 100 bytes', 'template.assignmentGroup is too long: it may take at most 200 bytes', 'template.assignedTo is too long: it may take at most 200 bytes', 'template.incident is too long: it may take at most 40 bytes', 'template.directBusinessService is too long: it may take at most 200 bytes', 'template.affectedClients is too long: it may take at most 2000 bytes', 'template.usersAffected is too long: it may take at most 2000 bytes', 'template.secureCodingTicket is too long: it may take at most 40 bytes', 'template.approvers.businessApprover is too long: it may take at most 200 bytes', 'template.planning.validationPlan is too long: it may take at most 2000 bytes', 'template.privilegedAccess.users[0].account is too long: it may take at most 200 bytes']
        'its texts fill their columns'           | [requestedFor: 'ł' * 100, department: 'é' * 50, assignmentGroup: 'ł' * 100, incident: 'é' * 20, usersAffected: 'ą' * 1000, secureCodingTicket: 'é' * 20, approvers: new Approvers('ż' * 100, 'ż' * 100, 'ż' * 100)] || []
    }

    def "a profile is created at version 0 with its tasks and changed only at the version it was read at"() {
        given:
        def stored = new ChangeProfile(4L, template(), tasks(), 2, EPOCH)

        expect:
        ChangeProfile.create(4L, template(), tasks(1)) == new ChangeProfile(4L, template(), tasks(1), 0, null)
        stored.change(2L, template(category: 'Hardware'), tasks(3)) ==
                new ChangeProfile(4L, template(category: 'Hardware'), tasks(3), 2, EPOCH)

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
        def edits = template(jiraProjectKey: 'OTHER', type: EMERGENCY, timing: new Timing('20:00', 5, 5),
                category: 'Hardware', release: 'CERT 4.3', downtime: true, requestedFor: 'Ann Lee',
                usersAffected: 'Operators', secureCodingTicket: 'APPSEC-7', riskAssessment: risk(businessImpact: 'High'))

        expect:
        raised.edited(edits) == template(category: 'Hardware', release: 'CERT 4.3', downtime: true,
                requestedFor: 'Ann Lee', usersAffected: 'Operators', secureCodingTicket: 'APPSEC-7',
                riskAssessment: risk(businessImpact: 'High'))
        raised.edited(edits).risk() == 'High'
    }

    def "a schedule is checked for presence and order: #expected"() {
        given:
        def problems = new ValidationProblems()

        when:
        schedule(edits).check(false, problems.at('schedule'))

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

    def "a schedule #needs a downtime window when the template #has downtime: #expected"() {
        given:
        def problems = new ValidationProblems()

        when:
        schedule(edits).check(downtime, problems.at('schedule'))

        then:
        problems.list().collect { "$it.field $it.message".toString() } == expected

        where:
        downtime | edits                                                                      || expected
        true     | [downtimeStart: '2026-10-10T06:00:00Z', downtimeEnd: '2026-10-10T08:00:00Z'] || []
        true     | [:]                                                                        || ['schedule.downtimeStart choose when the downtime starts', 'schedule.downtimeEnd choose when the downtime ends']
        true     | [downtimeStart: '2026-10-10T06:00:00Z']                                    || ['schedule.downtimeEnd choose when the downtime ends']
        true     | [downtimeStart: '2026-10-10T08:00:00Z', downtimeEnd: '2026-10-10T08:00:00Z'] || ['schedule.downtimeEnd must be after the downtime start']
        false    | [:]                                                                        || []
        false    | [downtimeStart: '2026-10-10T06:00:00Z', downtimeEnd: '2026-10-10T08:00:00Z'] || ['schedule.downtimeStart must be empty without downtime', 'schedule.downtimeEnd must be empty without downtime']
        false    | [downtimeEnd: '2026-10-10T08:00:00Z']                                      || ['schedule.downtimeEnd must be empty without downtime']

        needs = downtime ? 'needs' : 'takes no'
        has = downtime ? 'has' : 'has no'
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

    def "a schedule reads as its installation, validation, first usage and downtime"() {
        expect:
        schedule().text() == 'Installation 2026-10-10 06:00 to 10:00 UTC, post-install validation 2026-10-10 10:00' +
                ' to 11:00 UTC, first usage 2026-10-12 08:00 UTC. No downtime.'
        schedule(installationStart: '2026-10-09T22:00:00Z', downtimeStart: '2026-10-09T22:00:00Z',
                downtimeEnd: '2026-10-10T02:30:00Z').text() == 'Installation 2026-10-09 22:00 to 2026-10-10 10:00 UTC,' +
                ' post-install validation 2026-10-10 10:00 to 11:00 UTC, first usage 2026-10-12 08:00 UTC.' +
                ' Downtime 2026-10-09 22:00 to 2026-10-10 02:30 UTC.'
    }

    def "a schedule not planned yet reads as such"() {
        expect:
        ChangeSchedule.UNPLANNED.text() == 'Installation not planned yet, post-install validation not planned yet,' +
                ' first usage not planned yet. No downtime.'
        schedule(validationEnd: null, firstUsage: null).text() == 'Installation 2026-10-10 06:00 to 10:00 UTC,' +
                ' post-install validation not planned yet, first usage not planned yet. No downtime.'
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

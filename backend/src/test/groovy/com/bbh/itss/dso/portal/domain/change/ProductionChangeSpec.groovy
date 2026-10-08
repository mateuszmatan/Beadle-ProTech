package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.EMERGENCY
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.APPLIED
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.NOT_APPLIED
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.PENDING
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.requested
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SECTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.descriptionOf
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.draft
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.shortDescriptionOf
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.at
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeProduct
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template

class ProductionChangeSpec extends Specification {

    static final Planning PLANNING = new Planning('Tests passed on QC.', 'Deploy the services.',
            'Run the smoke tests.', 'Redeploy the previous release.', 'The owner confirms the first use.')
    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')

    def product = changeProduct()
    def epics = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]
    def stories = [story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record each change', 'CERT-5'),
                   story('CERT-3', 'Teams alert', 'CERT-1')]

    def "a draft writes the short description, the description and the change tasks of its template from Jira"() {
        when:
        def change = draft(product, 'Mateusz Matan', tasks(), FIX_VERSION, schedule(), template(planning: PLANNING),
                epics, stories, ' ', null)

        then:
        [change.id(), change.number(), change.url(), change.createdAt(), change.version()] == [null] * 5
        [change.productId(), change.productCode(), change.productName(), change.departmentId(),
         change.departmentName(), change.openedBy()] == [1L, 'CERTSCANNER', 'CertScanner', 3L, 'Corporate Technology',
                                                          'Mateusz Matan']
        change.fixVersion() == FIX_VERSION
        change.schedule() == schedule()
        change.template() == template(planning: PLANNING, release: FIX_VERSION, requestedFor: 'Mateusz Matan',
                requestedBy: 'Mateusz Matan', department: 'Corporate Technology', assignedTo: 'Mateusz Matan')
        change.epicKeys() == ['CERT-1', 'CERT-5']
        change.storyKeys() == ['CERT-2', 'CERT-6', 'CERT-3']
        [change.state(), change.workflow(), change.syncedAt(), change.syncProblem(), change.update()] ==
                [DRAFT, [], null, null, null]
        change.state().isOpen()
        change.shortDescription() == 'CertScanner CERT 4.2: Expiry alerts; Audit trail'
        change.description() == '''\
                Production release CERT 4.2 of CertScanner (CERTSCANNER) in Corporate Technology.
                Installation 2026-10-10 06:00 to 10:00 UTC, post-install validation 2026-10-10 10:00 to 11:00 UTC, first usage 2026-10-12 08:00 UTC. No downtime.

                Change tasks: Task 1 of the CertScanner release; Task 2 of the CertScanner release.

                Scope from Jira project CERT, FixVersion CERT 4.2:
                CERT-1 Expiry alerts (Done)
                - CERT-2 E-mail the owner (Done)
                - CERT-3 Teams alert (Done)
                CERT-5 Audit trail (Done)
                - CERT-6 Record each change (Done)

                Test summary:
                Tests passed on QC.

                Implementation plan:
                Deploy the services.

                Validation plan:
                Run the smoke tests.

                Backout plan:
                Redeploy the previous release.

                First use plan:
                The owner confirms the first use.

                Privileged access: not needed.

                Risk: Moderate
                Number of BBH workgroups impacted: Single
                Complexity of the change: Simple
                Number of BBH users impacted: 5-25
                Complexity of validation: Simple
                Number of applications impacted: Single
                Backout testing & duration: Less than 30 minutes
                Number of impacted clients outside BBH: No clients
                Platform status: Existing
                Business impact: Low

                About CertScanner:
                Watches TLS certificates.'''.stripIndent()
        change.tasks() == tasks().collect { new ChangeTask(null, it.shortDescription(), it.description(), OPEN) }
    }

    def "the description names the downtime, the privileged users, a missing risk assessment and the new fields"() {
        when:
        def text = descriptionOf(changeProduct(departmentName: null), tasks(1), 'R1', schedule(downtimeStart: '2026-10-10T06:00:00Z',
                downtimeEnd: '2026-10-10T08:00:00Z'), template(description: null, downtime: true,
                privilegedAccess: privileged(2), riskAssessment: RiskAssessment.NONE,
                usersAffected: 'Fund accountants', secureCodingTicket: 'APPSEC-1234'), epics.take(1), [])

        then:
        text.startsWith('Production release R1 of CertScanner (CERTSCANNER).\n')
        text.contains('first usage 2026-10-12 08:00 UTC. Downtime 2026-10-10 06:00 to 08:00 UTC.\n\nChange tasks:' +
                ' Task 1 of the CertScanner release.\n\n')
        text.contains('Scope from Jira project CERT, FixVersion R1:\nCERT-1 Expiry alerts (Done)\n\nTest summary:')
        text.contains('\nPrivileged access needed for: User 1 (adm_user1), User 2 (adm_user2).\n')
        text.endsWith('Risk: not assessed\n\nUsers affected:\nFund accountants\n\nSecure coding ticket: APPSEC-1234')
    }

    def "texts typed by the user replace the generated ones and a given release is kept"() {
        when:
        def change = draft(changeProduct(departmentId: null, departmentName: null), 'Mateusz Matan', tasks(1),
                FIX_VERSION, schedule(), template(release: 'Release 42'), epics, [], ' Mine ', ' My description ')

        then:
        change.shortDescription() == 'Mine'
        change.description() == 'My description'
        change.template().release() == 'Release 42'
        shortDescriptionOf(product, FIX_VERSION, []) == 'CertScanner CERT 4.2 production release'
    }

    def "long texts are cut to what ProTech takes"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Epic number $it with a long summary that goes on and on") }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'x' * 60, 'CERT-1') }
        def custody = changeProduct(departmentName: 'Custody')
        def wordy = template(planning: new Planning(*(['p' * 2000] * 5)), description: 'd' * 2000,
                usersAffected: 'u' * 2000, secureCodingTicket: 's' * 40)

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(custody, tasks(), FIX_VERSION, schedule(), template(), many, lots)
        def longest = descriptionOf(custody, tasks(), FIX_VERSION, schedule(), wordy, many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        summary.endsWith('...')
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
        bytes(longest) <= DESCRIPTION_MAX
        longest.contains('Test summary:\n' + 'p' * (SECTION_MAX - 3) + '...\n')
        longest.contains('\n\nUsers affected:\n' + 'u' * (SECTION_MAX - 3) + '...\n\nSecure coding ticket: sss')
    }

    def "texts with accented Jira summaries still fit the bytes of their Oracle columns"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Épique numéro $it – résumé très détaillé " + 'é' * 20) }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'ż' * 60, 'CERT-1') }
        def custody = changeProduct(departmentName: 'Custody')

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(custody, tasks(), FIX_VERSION, schedule(), template(), many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
    }

    def "a raised change is a draft of its raise time with its number, the numbers of its tasks in order and its link"() {
        given:
        def drafted = draft(changeProduct(departmentName: null), null, tasks(), FIX_VERSION, schedule(), template(),
                epics, [], null, null)

        when:
        def raised = drafted.raisedAt(RAISED).numbered('CHG0001', ['CTASK0001', 'CTASK0002'], 'https://snow/CHG0001')

        then:
        raised.number() == 'CHG0001'
        raised.url() == 'https://snow/CHG0001'
        raised.tasks()*.number() == ['CTASK0001', 'CTASK0002']
        raised.tasks()*.state() == [OPEN, OPEN]
        [raised.state(), raised.workflow(), raised.syncedAt(), raised.createdAt()] ==
                [DRAFT, [new WorkflowStep(DRAFT, RAISED)], RAISED, RAISED]
        [raised.fixVersion(), raised.schedule(), raised.template()] ==
                [drafted.fixVersion(), drafted.schedule(), drafted.template()]
        raised.shortDescription() == drafted.shortDescription()
        raised.description() == drafted.description()
    }

    def "#path is what ProTech has not applied when it holds another value"() {
        expect:
        raised().unappliedIn(raised(edit)) == [path]
        raised(edit).unappliedIn(raised()) == [path]

        where:
        path                               | edit
        'shortDescription'                 | [shortDescription: 'Other']
        'description'                      | [description: 'Other']
        'schedule.installationStart'       | [schedule: schedule(installationStart: '2026-10-10T05:00:00Z')]
        'schedule.installationEnd'         | [schedule: schedule(installationEnd: '2026-10-10T09:00:00Z')]
        'schedule.validationStart'         | [schedule: schedule(validationStart: '2026-10-10T10:30:00Z')]
        'schedule.validationEnd'           | [schedule: schedule(validationEnd: '2026-10-10T12:00:00Z')]
        'schedule.firstUsage'              | [schedule: schedule(firstUsage: '2026-10-13T08:00:00Z')]
        'schedule.downtimeStart'           | [schedule: schedule(downtimeStart: '2026-10-10T06:00:00Z')]
        'schedule.downtimeEnd'             | [schedule: schedule(downtimeEnd: '2026-10-10T08:00:00Z')]
        'template.requestedFor'            | [template: template(release: FIX_VERSION, requestedFor: 'Ann Lee')]
        'template.requestedBy'             | [template: template(release: FIX_VERSION, requestedBy: 'Ann Lee')]
        'template.department'              | [template: template(release: FIX_VERSION, department: 'Custody')]
        'template.assignmentGroup'         | [template: template(release: FIX_VERSION, assignmentGroup: 'Other')]
        'template.category'                | [template: template(release: FIX_VERSION, category: 'Other')]
        'template.assignedTo'              | [template: template(release: FIX_VERSION, assignedTo: 'Ann Lee')]
        'template.release'                 | [template: template(release: 'Other')]
        'template.configurationItem'       | [template: template(release: FIX_VERSION, configurationItem: 'Other')]
        'template.incident'                | [template: template(release: FIX_VERSION, incident: 'INC1')]
        'template.directBusinessService'   | [template: template(release: FIX_VERSION, directBusinessService: 'Payments')]
        'template.problem'                 | [template: template(release: FIX_VERSION, problem: 'PRB1')]
        'template.affectedClients'         | [template: template(release: FIX_VERSION, affectedClients: 'All')]
        'template.usersAffected'           | [template: template(release: FIX_VERSION, usersAffected: 'Operators')]
        'template.description'             | [template: template(release: FIX_VERSION, description: 'Other')]
        'template.approvers'               | [template: template(release: FIX_VERSION, approvers: Approvers.NONE)]
        'template.downtime'                | [template: template(release: FIX_VERSION, downtime: true)]
        'template.planning'                | [template: template(release: FIX_VERSION, planning: PLANNING)]
        'template.privilegedAccess'        | [template: template(release: FIX_VERSION, privilegedAccess: privileged(1))]
        'template.riskAssessment'          | [template: template(release: FIX_VERSION, riskAssessment: RiskAssessment.NONE)]
        'template.secureCodingTicket'      | [template: template(release: FIX_VERSION, secureCodingTicket: 'APPSEC-1')]
        'tasks'                            | [tasks: raised().tasks().take(1)]
        'tasks'                            | [tasks: raised().tasks().reverse()]
        'tasks'                            | [tasks: [raised().tasks()[0], new ChangeTask('CTASK0041002', 'Other', 'Step 2 of the CertScanner release.', OPEN)]]
    }

    def "the paths are listed in their order, and the fixed template fields, task states and canceled tasks never differ"() {
        given:
        def mine = raised()
        def all = raised(shortDescription: 'S', description: 'D',
                schedule: new ChangeSchedule(*(1..7).collect { at("2026-11-0${it}T08:00:00Z") }),
                template: new ChangeTemplate('OTHER', 'RF', 'RB', 'DE', 'G', 'C', 'AT', EMERGENCY, 'R', 'CI', 'I',
                        'DBS', 'P', 'High', 'A', 'UA', 'D', Approvers.NONE, true, new Timing('06:00', 1, 0), PLANNING,
                        privileged(1), RiskAssessment.NONE, 'SCT'),
                tasks: [])
        def same = raised(template: template(release: FIX_VERSION, jiraProjectKey: 'OTHER', type: EMERGENCY,
                timing: new Timing('06:00', 1, 0)),
                tasks: mine.tasks().collect { it.in(WORK_IN_PROGRESS) } + new ChangeTask('CTASK9', 'Gone', 'Gone.', CANCELED))

        expect:
        mine.unappliedIn(all) == ['shortDescription', 'description', 'schedule.installationStart',
                                  'schedule.installationEnd', 'schedule.validationStart', 'schedule.validationEnd',
                                  'schedule.firstUsage', 'schedule.downtimeStart', 'schedule.downtimeEnd',
                                  'template.requestedFor', 'template.requestedBy', 'template.department',
                                  'template.assignmentGroup', 'template.category', 'template.assignedTo',
                                  'template.release', 'template.configurationItem', 'template.incident',
                                  'template.directBusinessService', 'template.problem', 'template.affectedClients',
                                  'template.usersAffected', 'template.description', 'template.approvers',
                                  'template.downtime', 'template.planning', 'template.privilegedAccess',
                                  'template.riskAssessment', 'template.secureCodingTicket', 'tasks']
        mine.unappliedIn(same) == []
    }

    def "without an update in flight ProTech's values, tasks, state, workflow and link replace the stored ones"() {
        given:
        def applied = new ChangeUpdate(APPLIED, RAISED, 'Custody', [], null, RAISED)
        def stored = raised(update: applied, syncProblem: 'old problem')
        def remote = remote(shortDescription: 'Changed in ProTech', id: null, departmentId: null, epicKeys: [],
                template: template(release: 'R 9', jiraProjectKey: 'OTHER', type: EMERGENCY))

        when:
        def synced = stored.synced(remote, NOW)

        then:
        synced == stored.toBuilder().shortDescription('Changed in ProTech').template(template(release: 'R 9'))
                .tasks(remote.tasks()).state(IMPLEMENTATION).workflow(remote.workflow()).url('https://protech/CHG')
                .syncedAt(NOW).syncProblem(null).build()
        synced.update() == applied
        synced.differsFrom(stored)
        !stored.synced(raised(), NOW).differsFrom(stored)
        stored.synced(raised(), NOW).syncedAt() == NOW
    }

    def "an update ProTech #outcome is #status with the paths it did not apply, checked now"() {
        given:
        def stored = raised(shortDescription: 'Requested', update: requested(NOW.minusSeconds(age), 'Custody'))
        def remote = remote(shortDescription: holds)

        when:
        def synced = stored.synced(remote, NOW)

        then:
        synced.update() == new ChangeUpdate(status, NOW.minusSeconds(age), 'Custody', fields, message, NOW)
        synced.shortDescription() == shown
        [synced.state(), synced.workflow(), synced.url(), synced.syncedAt()] ==
                [IMPLEMENTATION, remote.workflow(), 'https://protech/CHG', NOW]
        synced.tasks()*.state() == [WORK_IN_PROGRESS, WORK_IN_PROGRESS]

        where:
        outcome                       | age | holds       || status      | fields               | message             | shown
        'applied'                     | 5   | 'Requested' || APPLIED     | []                   | null                | 'Requested'
        'has not applied yet'         | 59  | 'Old'       || PENDING     | ['shortDescription'] | null                | 'Requested'
        'did not apply within a minute' | 60 | 'Old'      || NOT_APPLIED | ['shortDescription'] | NOT_APPLIED_MESSAGE | 'Old'
    }

    def "a pending update checked again differs only when more than its check time changed"() {
        given:
        def stored = raised(shortDescription: 'Requested', update: requested(NOW.minusSeconds(10), 'Custody'))
                .synced(remote(shortDescription: 'Old'), NOW.minusSeconds(5))

        expect:
        stored.update().checkedAt() == NOW.minusSeconds(5)
        !stored.synced(remote(shortDescription: 'Old'), NOW).differsFrom(stored)
        stored.synced(remote(shortDescription: 'Old'), NOW).update().checkedAt() == NOW
        stored.synced(remote(shortDescription: 'Old', description: 'Other'), NOW).differsFrom(stored)
        stored.synced(remote(shortDescription: 'Requested'), NOW).differsFrom(stored)
        stored.synced(remote(shortDescription: 'Old', state: BUSINESS_APPROVAL), NOW).differsFrom(stored)
        !raised().synced(raised(), NOW).differsFrom(raised())
    }

    def "a pending update keeps the requested tasks with the states ProTech gives the tasks it knows"() {
        given:
        def requested = raised(update: requested(NOW, 'Custody'), tasks: raised().tasks().take(1) +
                new ChangeTask(null, 'New task', 'A new task.', OPEN))

        when:
        def synced = requested.synced(remote(), NOW)

        then:
        synced.update().fields() == ['tasks']
        synced.tasks() == [raised().tasks()[0].in(WORK_IN_PROGRESS), new ChangeTask(null, 'New task', 'A new task.', OPEN)]
    }

    def "an update that could not be checked waits with every path it changed and says why"() {
        given:
        def stored = raised()
        def published = raised(description: 'New', update: requested(NOW, 'Custody'),
                schedule: schedule(firstUsage: '2026-10-13T08:00:00Z'))

        expect:
        published.unverified(stored, 'ProTech could not be reached: down') ==
                published.toBuilder().update(new ChangeUpdate(PENDING, NOW, 'Custody',
                        ['description', 'schedule.firstUsage'], null, null))
                        .syncProblem('ProTech could not be reached: down').build()
        stored.withSyncProblem('gone').syncProblem() == 'gone'
        !raised(state: ChangeState.CLOSED).state().isOpen()
    }

    def "an edit takes the texts, the schedule, the editable ProTech fields and the tasks with their ProTech states"() {
        given:
        def stored = raised(tasks: [new ChangeTask('CTASK1', 'One', 'First.', WORK_IN_PROGRESS),
                                    new ChangeTask('CTASK2', 'Two', 'Second.', CANCELED),
                                    new ChangeTask('CTASK3', 'Three', 'Third.', CLOSED)])
        def edit = template(jiraProjectKey: 'OTHER', type: EMERGENCY, timing: new Timing('06:00', 1, 0),
                category: ' Hardware ', release: 'R 5', requestedFor: ' Ann Lee ', usersAffected: 'Operators')

        when:
        def edited = stored.edited(' New ', ' Text ', schedule(firstUsage: '2026-10-13T08:00:00Z'), edit,
                [new ChangeTask('CTASK3', 'Three', 'Third.', OPEN), new ChangeTask(null, ' Four ', 'Fourth.', CLOSED),
                 new ChangeTask('CTASK1', 'One more', 'First again.', CLOSED)], NOW).rebasedOn(stored)

        then:
        edited == stored.toBuilder().shortDescription('New').description('Text')
                .schedule(schedule(firstUsage: '2026-10-13T08:00:00Z'))
                .template(template(category: 'Hardware', release: 'R 5', requestedFor: 'Ann Lee',
                        usersAffected: 'Operators'))
                .tasks([new ChangeTask('CTASK3', 'Three', 'Third.', CLOSED), new ChangeTask(null, 'Four', 'Fourth.', OPEN),
                        new ChangeTask('CTASK1', 'One more', 'First again.', WORK_IN_PROGRESS),
                        new ChangeTask('CTASK2', 'Two', 'Second.', CANCELED)]).build()
    }

    def "an edit with #problem is refused against its field"() {
        given:
        def stored = raised(tasks: [new ChangeTask('CTASK1', 'One', 'First.', OPEN),
                                    new ChangeTask('CTASK2', 'Two', 'Second.', CANCELED),
                                    new ChangeTask('CTASK3', 'Three', 'Third.', CLOSED)])
        def values = [shortDescription: 'Short', description: 'Text', schedule: schedule(), template: template(),
                      tasks: [new ChangeTask('CTASK1', 'One', 'First.', OPEN),
                              new ChangeTask('CTASK3', 'Three', 'Third.', CLOSED)]] + edits

        when:
        stored.edited(values.shortDescription, values.description, values.schedule, values.template, values.tasks,
                Instant.parse('2026-10-10T07:00:00Z')).rebasedOn(stored)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems().collect { it.field() + ': ' + it.message() } == problems

        where:
        problem                        | edits                                           || problems
        'no short description'         | [shortDescription: ' ']                         || ['shortDescription: is required']
        'a long short description'     | [shortDescription: 'é' * 81]                    || ['shortDescription: is too long: it may take at most 160 bytes']
        'no description'               | [description: null]                             || ['description: is required']
        'a long description'           | [description: 'ł' * 2001]                       || ['description: is too long: it may take at most 4000 bytes']
        'no template'                  | [template: null]                                || ['template: fill in the ProTech fields of the change']
        'a broken template'            | [template: template(assignmentGroup: ' ')]      || ['template.assignmentGroup: is required']
        'no schedule'                  | [schedule: null]                                || ['schedule: choose when the change is installed, validated and first used']
        'a broken schedule'            | [schedule: schedule(firstUsage: '2026-10-10T10:30:00Z')] || ['schedule.firstUsage: must not be before the validation end']
        'a start moved into the past'  | [schedule: schedule(installationStart: '2026-10-10T05:00:00Z')] || ['schedule.installationStart: must be in the future']
        'downtime without its window'  | [template: template(downtime: true)]            || ['schedule.downtimeStart: choose when the downtime starts', 'schedule.downtimeEnd: choose when the downtime ends']
        'a window without downtime'    | [schedule: schedule(downtimeStart: '2026-10-10T06:00:00Z', downtimeEnd: '2026-10-10T08:00:00Z')] || ['schedule.downtimeStart: must be empty without downtime', 'schedule.downtimeEnd: must be empty without downtime']
        'a category off the list'      | [template: template(category: 'Software')]      || ['template.category: must be one of Application, Hardware, Infrastructure, System Software, Network, Telecom, Data Amendment, Desktop Software, Storage, Facilities, Other, Database']
        'no tasks'                     | [tasks: []]                                     || ['tasks: add at least one change task']
        'a task without texts'         | [tasks: [new ChangeTask(null, ' ', 'x' * 4001, null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].shortDescription: is required', 'tasks[0].description: is too long: it may take at most 4000 bytes']
        'a task of another change'     | [tasks: [new ChangeTask('CTASK9', 'Nine', 'Ninth.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is not a change task of CHG0031001']
        'a task listed twice'          | [tasks: [new ChangeTask('CTASK1', 'One', 'First.', null), new ChangeTask('CTASK1', 'One', 'First.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[1].number: is listed more than once']
        'a canceled task'              | [tasks: [new ChangeTask('CTASK2', 'Two', 'Second.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is canceled in ProTech']
        'a closed task changed'        | [tasks: [new ChangeTask('CTASK3', 'Three', 'Changed.', null)]] || ['tasks[0].number: is closed in ProTech and cannot be changed']
        'a closed task removed'        | [tasks: [new ChangeTask('CTASK1', 'One', 'First.', null)]] || ['tasks: CTASK3 is closed in ProTech and cannot be removed']
        'too many tasks'               | [tasks: (1..50).collect { new ChangeTask(null, "T$it", 'Text.', null) } + new ChangeTask('CTASK3', 'Three', 'Third.', null)] || ['tasks: may list at most 50 change tasks']
    }

    def "an edit is checked against the tasks ProTech holds now and takes the rest of the change from there"() {
        given:
        def stored = raised(tasks: [new ChangeTask('CTASK1', 'One', 'First.', OPEN)])
        def current = stored.toBuilder().state(IMPLEMENTATION).version(3L)
                .tasks([new ChangeTask('CTASK1', 'One', 'First.', CLOSED)]).build()
        def kept = stored.edited('New', 'Text', schedule(), template(), [new ChangeTask('CTASK1', 'One', 'First.', null)],
                NOW)
        def changed = stored.edited('New', 'Text', schedule(), template(),
                [new ChangeTask('CTASK1', 'One', 'Changed.', null)], NOW)

        when:
        def rebased = kept.rebasedOn(current)

        then:
        rebased == current.toBuilder().shortDescription('New').description('Text').template(template(release: FIX_VERSION))
                .build()

        when:
        changed.rebasedOn(current)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field() == ['tasks[0].number']
        changed.rebasedOn(stored).tasks() == [new ChangeTask('CTASK1', 'One', 'Changed.', OPEN)]
    }

    def "an edit without a release releases the change as its FixVersion"() {
        expect:
        raised().edited('Short', 'Text', schedule(), template(release: ' '), raised().tasks(), NOW)
                .template().release() == raised().fixVersion()
    }

    def "an edit keeps an installation start that has passed when it does not move it"() {
        expect:
        raised().edited('Short', 'Text', schedule(), template(), raised().tasks(), Instant.parse('2026-10-11T00:00:00Z'))
                .schedule() == schedule()
    }

    static ProductionChange remote(Map changes = [:]) {
        raised([url: 'https://protech/CHG', state: IMPLEMENTATION,
                workflow: [new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120)),
                           new WorkflowStep(IMPLEMENTATION, RAISED.plusSeconds(600))],
                tasks: raised().tasks().collect { it.in(WORK_IN_PROGRESS) }] + changes)
    }
}

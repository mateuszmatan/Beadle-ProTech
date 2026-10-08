package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
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
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ProductionChangeSpec extends Specification {

    static final Planning PLANNING = new Planning('Tests passed on QC.', 'Deploy the services.',
            'Run the smoke tests.', 'Redeploy the previous release.', 'The owner confirms the first use.')
    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')

    def product = product(code: 'CERTSCANNER')
    def epics = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]
    def stories = [story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record each change', 'CERT-5'),
                   story('CERT-3', 'Teams alert', 'CERT-1')]

    def "a draft writes the short description, the description and the change tasks of its template from Jira"() {
        when:
        def change = draft(product, 3L, 'Corporate Technology', tasks(), FIX_VERSION, schedule(),
                template(planning: PLANNING), epics, stories, ' ', null)

        then:
        [change.id(), change.number(), change.url(), change.createdAt(), change.version()] == [null] * 5
        [change.productId(), change.productCode(), change.productName(), change.departmentId(),
         change.departmentName()] == [1L, 'CERTSCANNER', 'CertScanner', 3L, 'Corporate Technology']
        change.fixVersion() == FIX_VERSION
        change.schedule() == schedule()
        change.template() == template(planning: PLANNING, release: FIX_VERSION)
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

                Risk assessment:
                BBH workgroups impacted: 1
                BBH users impacted: 10
                BBH applications impacted: 1
                Impacted clients: 0
                Impacted clients outside BBH: 0
                Business impact: Low
                Complexity of change: Low
                Complexity of validation: Low
                Backout testing and duration: Tested on QC, about 15 minutes
                Platform status: Existing platform

                About CertScanner:
                Watches TLS certificates.'''.stripIndent()
        change.tasks() == tasks().collect { new ChangeTask(null, it.shortDescription(), it.description(), OPEN) }
    }

    def "the description names the downtime, the privileged users and a missing risk assessment"() {
        when:
        def text = descriptionOf(product, null, tasks(1), 'R1', schedule(), template(description: null,
                downtime: true, privilegedAccess: privileged(2), riskAssessment: RiskAssessment.NONE), epics.take(1),
                [])

        then:
        text.startsWith('Production release R1 of CertScanner (CERTSCANNER).\n')
        text.contains('first usage 2026-10-12 08:00 UTC. Downtime expected during the installation.\n')
        text.contains('\n\nChange tasks: Task 1 of the CertScanner release.\n\n')
        text.contains('Scope from Jira project CERT, FixVersion R1:\nCERT-1 Expiry alerts (Done)\n\nTest summary:')
        text.contains('\nPrivileged access needed for: User 1 (adm_user1), User 2 (adm_user2).\n')
        text.endsWith('Risk assessment:\nNot assessed.')
    }

    def "texts typed by the user replace the generated ones and a given release is kept"() {
        when:
        def change = draft(product, null, null, tasks(1), FIX_VERSION, schedule(), template(release: 'Release 42'),
                epics, [], ' Mine ', ' My description ')

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
        def wordy = template(planning: new Planning(*(['p' * 2000] * 5)), description: 'd' * 2000,
                riskAssessment: new RiskAssessment(1, 2, 3, 4, 5, 'b' * 100, 'c' * 100, 'v' * 100, 'o' * 2000, 's' * 100))

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(product, 'Custody', tasks(), FIX_VERSION, schedule(), template(), many, lots)
        def longest = descriptionOf(product, 'Custody', tasks(), FIX_VERSION, schedule(), wordy, many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        summary.endsWith('...')
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
        bytes(longest) <= DESCRIPTION_MAX
        longest.contains('Test summary:\n' + 'p' * (SECTION_MAX - 3) + '...\n')
        longest.contains('Backout testing and duration: ooo')
    }

    def "texts with accented Jira summaries still fit the bytes of their Oracle columns"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Épique numéro $it – résumé très détaillé " + 'é' * 20) }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'ż' * 60, 'CERT-1') }

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(product, 'Custody', tasks(), FIX_VERSION, schedule(), template(), many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
    }

    def "a raised change is a draft of its raise time with its number, the numbers of its tasks in order and its link"() {
        given:
        def drafted = draft(product, 3L, null, tasks(), FIX_VERSION, schedule(), template(), epics, [], null, null)

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
        'template.assignmentGroup'         | [template: template(release: FIX_VERSION, assignmentGroup: 'Other')]
        'template.category'                | [template: template(release: FIX_VERSION, category: 'Other')]
        'template.configurationItem'       | [template: template(release: FIX_VERSION, configurationItem: 'Other')]
        'template.release'                 | [template: template(release: 'Other')]
        'template.incident'                | [template: template(release: FIX_VERSION, incident: 'INC1')]
        'template.problem'                 | [template: template(release: FIX_VERSION, problem: 'PRB1')]
        'template.affectedClients'         | [template: template(release: FIX_VERSION, affectedClients: 'All')]
        'template.description'             | [template: template(release: FIX_VERSION, description: 'Other')]
        'template.approvers'               | [template: template(release: FIX_VERSION, approvers: Approvers.NONE)]
        'template.downtime'                | [template: template(release: FIX_VERSION, downtime: true)]
        'template.planning'                | [template: template(release: FIX_VERSION, planning: PLANNING)]
        'template.privilegedAccess'        | [template: template(release: FIX_VERSION, privilegedAccess: privileged(1))]
        'template.riskAssessment'          | [template: template(release: FIX_VERSION, riskAssessment: RiskAssessment.NONE)]
        'tasks'                            | [tasks: raised().tasks().take(1)]
        'tasks'                            | [tasks: raised().tasks().reverse()]
        'tasks'                            | [tasks: [raised().tasks()[0], new ChangeTask('CTASK0041002', 'Other', 'Step 2 of the CertScanner release.', OPEN)]]
    }

    def "the paths are listed in their order, and the fixed template fields, task states and canceled tasks never differ"() {
        given:
        def mine = raised()
        def all = raised(shortDescription: 'S', description: 'D',
                schedule: new ChangeSchedule(*(1..5).collect { at("2026-11-0${it}T08:00:00Z") }),
                template: new ChangeTemplate('OTHER', 'G', 'C', EMERGENCY, 'CI', 'R', 'I', 'P', 'A', 'D',
                        Approvers.NONE, true, new Timing('06:00', 1, 0), PLANNING, privileged(1), RiskAssessment.NONE),
                tasks: [])
        def same = raised(template: template(release: FIX_VERSION, jiraProjectKey: 'OTHER', type: EMERGENCY,
                timing: new Timing('06:00', 1, 0)),
                tasks: mine.tasks().collect { it.in(WORK_IN_PROGRESS) } + new ChangeTask('CTASK9', 'Gone', 'Gone.', CANCELED))

        expect:
        mine.unappliedIn(all) == ['shortDescription', 'description', 'schedule.installationStart',
                                  'schedule.installationEnd', 'schedule.validationStart', 'schedule.validationEnd',
                                  'schedule.firstUsage', 'template.assignmentGroup', 'template.category',
                                  'template.configurationItem', 'template.release', 'template.incident',
                                  'template.problem', 'template.affectedClients', 'template.description',
                                  'template.approvers', 'template.downtime', 'template.planning',
                                  'template.privilegedAccess', 'template.riskAssessment', 'tasks']
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
                category: ' Apps ', release: 'R 5')

        when:
        def edited = stored.edited(' New ', ' Text ', schedule(firstUsage: '2026-10-13T08:00:00Z'), edit,
                [new ChangeTask('CTASK3', 'Three', 'Third.', OPEN), new ChangeTask(null, ' Four ', 'Fourth.', CLOSED),
                 new ChangeTask('CTASK1', 'One more', 'First again.', CLOSED)], NOW)

        then:
        edited == stored.toBuilder().shortDescription('New').description('Text')
                .schedule(schedule(firstUsage: '2026-10-13T08:00:00Z'))
                .template(template(category: 'Apps', release: 'R 5'))
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
                Instant.parse('2026-10-10T07:00:00Z'))

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
        'no tasks'                     | [tasks: []]                                     || ['tasks: add at least one change task', 'tasks: CTASK3 is closed in ProTech and cannot be removed']
        'a task without texts'         | [tasks: [new ChangeTask(null, ' ', 'x' * 4001, null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].shortDescription: is required', 'tasks[0].description: is too long: it may take at most 4000 bytes']
        'a task of another change'     | [tasks: [new ChangeTask('CTASK9', 'Nine', 'Ninth.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is not a change task of CHG0031001']
        'a task listed twice'          | [tasks: [new ChangeTask('CTASK1', 'One', 'First.', null), new ChangeTask('CTASK1', 'One', 'First.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[1].number: is listed more than once']
        'a canceled task'              | [tasks: [new ChangeTask('CTASK2', 'Two', 'Second.', null), new ChangeTask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is canceled in ProTech']
        'a closed task changed'        | [tasks: [new ChangeTask('CTASK3', 'Three', 'Changed.', null)]] || ['tasks[0].number: is closed in ProTech and cannot be changed']
        'a closed task removed'        | [tasks: [new ChangeTask('CTASK1', 'One', 'First.', null)]] || ['tasks: CTASK3 is closed in ProTech and cannot be removed']
        'too many tasks'               | [tasks: (1..50).collect { new ChangeTask(null, "T$it", 'Text.', null) } + new ChangeTask('CTASK3', 'Three', 'Third.', null)] || ['tasks: may list at most 50 change tasks']
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

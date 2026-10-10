package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L1
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L2
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.SUPPORT
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL
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
import static com.bbh.itss.dso.portal.support.ChangeFixtures.ctask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.details
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.releaseTask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCoding
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.task
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template

class ProductionChangeSpec extends Specification {

    static final Planning PLANNING = new Planning('Tests passed on QC.', 'Deploy the services.',
            'Run the smoke tests.', 'Redeploy the previous release.', 'The owner confirms the first use.')
    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')

    def product = changeProduct()
    def epics = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]
    def stories = [story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record each change', 'CERT-5'),
                   story('CERT-3', 'Teams alert', 'CERT-1')]

    def "a draft writes the texts from Jira, has no change tasks and no secure coding ticket yet but keeps its inputs"() {
        when:
        def change = draft(product, 'Mateusz Matan', FIX_VERSION, schedule(), template(planning: PLANNING,
                secureCodingTicket: 'SCP-1', secureCoding: secureCoding()),
                epics, stories, ' ', null)

        then:
        [change.id(), change.number(), change.url(), change.createdAt(), change.version()] == [null] * 5
        [change.productId(), change.productCode(), change.productName(), change.departmentId(),
         change.departmentName(), change.openedBy()] == [1L, 'CERTSCANNER', 'CertScanner', 3L, 'Corporate Technology',
                                                          'Mateusz Matan']
        change.fixVersion() == FIX_VERSION
        change.schedule() == schedule()
        change.template() == template(planning: PLANNING, release: FIX_VERSION, requestedFor: 'Mateusz Matan',
                requestedBy: 'Mateusz Matan', department: 'Corporate Technology', assignedTo: 'Mateusz Matan',
                secureCoding: secureCoding())
        change.epicKeys() == ['CERT-1', 'CERT-5']
        change.storyKeys() == ['CERT-2', 'CERT-6', 'CERT-3']
        [change.state(), change.workflow(), change.syncedAt(), change.syncProblem(), change.update()] ==
                [DRAFT, [], null, null, null]
        change.state().isOpen()
        change.shortDescription() == 'CertScanner CERT 4.2: Expiry alerts; Audit trail'
        change.description() == '''\
                Production release CERT 4.2 of CertScanner (CERTSCANNER) in Corporate Technology.
                Installation 2026-10-10 06:00 to 10:00 UTC, post-install validation 2026-10-10 10:00 to 11:00 UTC, first usage 2026-10-12 08:00 UTC. No downtime.

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
                Business impact: Low'''.stripIndent()
        change.tasks() == []
    }

    def "the description names the downtime, the privileged users, the default risk answers and the new fields"() {
        when:
        def text = descriptionOf(changeProduct(departmentName: null), 'R1', schedule(downtimeStart: '2026-10-10T06:00:00Z',
                downtimeEnd: '2026-10-10T08:00:00Z'), template(downtime: true,
                privilegedAccess: privileged(2), riskAssessment: RiskAssessment.DEFAULTS,
                usersAffected: 'Fund accountants', secureCodingTicket: 'SCP-1234'), epics.take(1), [])

        then:
        text.startsWith('Production release R1 of CertScanner (CERTSCANNER).\n')
        text.contains('first usage 2026-10-12 08:00 UTC. Downtime 2026-10-10 06:00 to 08:00 UTC.\n\nScope from')
        text.contains('Scope from Jira project CERT, FixVersion R1:\nCERT-1 Expiry alerts (Done)\n\nTest summary:')
        text.contains('\nPrivileged access needed for: User 1 (adm_user1), User 2 (adm_user2).\n')
        text.contains('\nRisk: Low\nNumber of BBH workgroups impacted: Single\n')
        text.endsWith('Business impact: None\n\nUsers affected:\nFund accountants\n\nSecure coding ticket: SCP-1234')
    }

    def "texts typed by the user replace the generated ones and a given release is kept"() {
        when:
        def change = draft(changeProduct(departmentId: null, departmentName: null), 'Mateusz Matan', FIX_VERSION, schedule(), template(release: 'Release 42'), epics, [], ' Mine ', ' My description ')

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
        def wordy = template(planning: new Planning(*(['p' * 2000] * 5)),
                usersAffected: 'u' * 2000, secureCodingTicket: 's' * 40)

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(custody, FIX_VERSION, schedule(), template(), many, lots)
        def longest = descriptionOf(custody, FIX_VERSION, schedule(), wordy, many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        summary.endsWith('...')
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('Business impact: Low')
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
        def text = descriptionOf(custody, FIX_VERSION, schedule(), template(), many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('Business impact: Low')
    }

    def "a raised change is a draft of its raise time with its number and its link"() {
        given:
        def drafted = draft(changeProduct(departmentName: null), null, FIX_VERSION, schedule(), template(),
                epics, [], null, null)

        when:
        def raised = drafted.raisedAt(RAISED).numbered('CHG0001', 'https://snow/CHG0001')

        then:
        raised.number() == 'CHG0001'
        raised.url() == 'https://snow/CHG0001'
        raised.tasks() == []
        [raised.state(), raised.workflow(), raised.syncedAt(), raised.createdAt()] ==
                [DRAFT, [new WorkflowStep(DRAFT, RAISED)], RAISED, RAISED]
        [raised.fixVersion(), raised.schedule(), raised.template()] ==
                [drafted.fixVersion(), drafted.schedule(), drafted.template()]
        raised.shortDescription() == drafted.shortDescription()
        raised.description() == drafted.description()
        raised.approvals() == [new ChangeApproval(BUSINESS, 'Rebecca Lawson', NOT_APPROVED, null),
                               new ChangeApproval(L1, 'Olivia Bennett', NOT_APPROVED, null),
                               new ChangeApproval(L2, 'James Carter', NOT_APPROVED, null),
                               new ChangeApproval(SUPPORT, 'Jane Smith', NOT_APPROVED, null)]
    }

    def "a secure coding ticket is stored with the inputs it was created from and changes nothing else"() {
        given:
        def change = raised()
        def inputs = secureCoding(apoNumber: 'APO-777')

        when:
        def ticketed = change.withSecureCoding(inputs, 'SCP-1001')

        then:
        ticketed.template() == change.template().toBuilder().secureCoding(inputs).secureCodingTicket('SCP-1001').build()
        ticketed.toBuilder().template(change.template()).build() == change
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
        'template.approvers'               | [template: template(release: FIX_VERSION, approvers: Approvers.NONE)]
        'template.downtime'                | [template: template(release: FIX_VERSION, downtime: true)]
        'template.planning'                | [template: template(release: FIX_VERSION, planning: PLANNING)]
        'template.privilegedAccess'        | [template: template(release: FIX_VERSION, privilegedAccess: privileged(1))]
        'template.riskAssessment'          | [template: template(release: FIX_VERSION, riskAssessment: RiskAssessment.DEFAULTS)]
        'template.secureCodingTicket'      | [template: template(release: FIX_VERSION, secureCodingTicket: 'APPSEC-1')]
        'tasks'                            | [tasks: raised().tasks().take(1)]
        'tasks'                            | [tasks: raised().tasks().reverse()]
        'tasks'                            | [tasks: [raised().tasks()[0], ctask('CTASK0041002', 'Other', 'Step 2 of the CertScanner release.', OPEN)]]
    }

    def "the paths are listed in their order, and the fixed template fields, task states and canceled tasks never differ"() {
        given:
        def mine = raised()
        def all = raised(shortDescription: 'S', description: 'D',
                schedule: new ChangeSchedule(*(1..7).collect { at("2026-11-0${it}T08:00:00Z") }),
                template: new ChangeTemplate('OTHER', 'RF', 'RB', 'DE', 'G', 'C', 'AT', EMERGENCY, 'R', 'CI', 'I',
                        'DBS', 'P', 'High', 'A', 'UA', Approvers.NONE, true, new Timing('06:00', 1, 0), PLANNING,
                        privileged(1), RiskAssessment.DEFAULTS, 'SCT', secureCoding()),
                tasks: [])
        def same = raised(template: template(release: FIX_VERSION, jiraProjectKey: 'OTHER', type: EMERGENCY,
                timing: new Timing('06:00', 1, 0)),
                tasks: mine.tasks().collect { it.in(WORK_IN_PROGRESS) } + ctask('CTASK9', 'Gone', 'Gone.', CANCELED))

        expect:
        mine.unappliedIn(all) == ['shortDescription', 'description', 'schedule.installationStart',
                                  'schedule.installationEnd', 'schedule.validationStart', 'schedule.validationEnd',
                                  'schedule.firstUsage', 'schedule.downtimeStart', 'schedule.downtimeEnd',
                                  'template.requestedFor', 'template.requestedBy', 'template.department',
                                  'template.assignmentGroup', 'template.category', 'template.assignedTo',
                                  'template.release', 'template.configurationItem', 'template.incident',
                                  'template.directBusinessService', 'template.problem', 'template.affectedClients',
                                  'template.usersAffected', 'template.approvers',
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
                ctask(null, 'New task', 'A new task.', OPEN))

        when:
        def synced = requested.synced(remote(), NOW)

        then:
        synced.update().fields() == ['tasks']
        synced.tasks() == [raised().tasks()[0].in(WORK_IN_PROGRESS).withApproval(APPROVED, ['Daniel Foster']),
                           ctask(null, 'New task', 'A new task.', OPEN)]
        synced.state() == CTASK_APPROVAL
    }

    def "a change ProTech holds in Implementation is In Progress in Beadle only when #tasks"() {
        expect:
        raised().synced(remote(tasks: tasks), NOW).state() == state
        raised().synced(remote(state: ChangeState.CLOSED, tasks: []), NOW).state() == ChangeState.CLOSED

        where:
        tasks                                                                             || state
        []                                                                                || CTASK_APPROVAL
        [task(1).withApproval(APPROVED, []), task(2).withApproval(REQUESTED, [])]         || CTASK_APPROVAL
        [task(1).withApproval(NOT_APPROVED, []), task(2).in(CANCELED)]                    || CTASK_APPROVAL
        [task(1).withApproval(APPROVED, []), task(2).in(CANCELED)]                        || IMPLEMENTATION
        [task(1).withApproval(APPROVED, []), task(2).withApproval(APPROVED, [])]          || IMPLEMENTATION
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
        def stored = raised(tasks: [ctask('CTASK1', 'One', 'First.', WORK_IN_PROGRESS),
                                    ctask('CTASK2', 'Two', 'Second.', CANCELED),
                                    ctask('CTASK3', 'Three', 'Third.', CLOSED)])
        def edit = template(jiraProjectKey: 'OTHER', type: EMERGENCY, timing: new Timing('06:00', 1, 0),
                category: ' Hardware ', release: 'R 5', requestedFor: ' Ann Lee ', usersAffected: 'Operators')

        when:
        def edited = stored.edited(' New ', ' Text ', schedule(firstUsage: '2026-10-13T08:00:00Z'), edit,
                [ctask('CTASK3', 'Three', 'Third.', OPEN), ctask(null, ' Four ', 'Fourth.', CLOSED),
                 ctask('CTASK1', 'One more', 'First again.', CLOSED)], NOW).rebasedOn(stored)

        then:
        edited == stored.toBuilder().shortDescription('New').description('Text')
                .schedule(schedule(firstUsage: '2026-10-13T08:00:00Z'))
                .template(template(category: 'Hardware', release: 'R 5', requestedFor: 'Ann Lee',
                        usersAffected: 'Operators'))
                .tasks([ctask('CTASK3', 'Three', 'Third.', CLOSED), ctask(null, 'Four', 'Fourth.', OPEN),
                        ctask('CTASK1', 'One more', 'First again.', WORK_IN_PROGRESS),
                        ctask('CTASK2', 'Two', 'Second.', CANCELED)]).build()
    }

    def "an edit with #problem is refused against its field"() {
        given:
        def stored = raised(tasks: [ctask('CTASK1', 'One', 'First.', OPEN),
                                    ctask('CTASK2', 'Two', 'Second.', CANCELED),
                                    ctask('CTASK3', 'Three', 'Third.', CLOSED)])
        def values = [shortDescription: 'Short', description: 'Text', schedule: schedule(), template: template(),
                      tasks: [ctask('CTASK1', 'One', 'First.', OPEN),
                              ctask('CTASK3', 'Three', 'Third.', CLOSED)]] + edits

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
        'a task without texts'         | [tasks: [ctask(null, ' ', 'x' * 4001, null), ctask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].details.shortDescription: is required', 'tasks[0].details.description: is too long: it may take at most 4000 bytes']
        'a release task out of window' | [tasks: [releaseTask([:], '2026-10-10T11:00:00Z'), ctask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].start: must not be after the installation end']
        'a task of another change'     | [tasks: [ctask('CTASK9', 'Nine', 'Ninth.', null), ctask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is not a change task of CHG0031001']
        'a task listed twice'          | [tasks: [ctask('CTASK1', 'One', 'First.', null), ctask('CTASK1', 'One', 'First.', null), ctask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[1].number: is listed more than once']
        'a canceled task'              | [tasks: [ctask('CTASK2', 'Two', 'Second.', null), ctask('CTASK3', 'Three', 'Third.', null)]] || ['tasks[0].number: is canceled in ProTech']
        'a closed task changed'        | [tasks: [ctask('CTASK3', 'Three', 'Changed.', null)]] || ['tasks[0].number: is closed in ProTech and cannot be changed']
        'a closed task removed'        | [tasks: [ctask('CTASK1', 'One', 'First.', null)]] || ['tasks: CTASK3 is closed in ProTech and cannot be removed']
        'too many tasks'               | [tasks: (1..50).collect { ctask(null, "T$it", 'Text.', null) } + ctask('CTASK3', 'Three', 'Third.', null)] || ['tasks: may list at most 50 change tasks']
    }

    def "an open change without change tasks yet can be edited"() {
        expect:
        raised(tasks: []).edited('Short', 'Text', schedule(), template(), [], NOW).tasks() == []
    }

    def "the tasks created on a raised change are planned in its window, take its affected CI and stay open"() {
        given:
        def change = raised(tasks: [task(1)])

        when:
        def planned = change.plannedTasks([releaseTask([configurationItem: null], null).numbered('CTASK9')
                                                   .withApproval(APPROVED, ['Ann Lee']).in(CLOSED), ChangeTask.of(details(2))])

        then:
        planned == [releaseTask([configurationItem: 'CertScanner'], '2026-10-10T06:01:00Z'),
                    ChangeTask.of(details(2, [configurationItem: 'CertScanner']))]
        change.withTasks(planned.collect { it.numbered('CTASK7') }).tasks() ==
                [task(1)] + planned.collect { it.numbered('CTASK7') }
    }

    def "the tasks created on a raised change are refused when #problem"() {
        when:
        raised(tasks: [task(1)] * held).plannedTasks(requested)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems().collect { it.field() + ': ' + it.message() } == problems

        where:
        problem                    | held | requested                                        || problems
        'a release task starts at the installation start' | 0 | [releaseTask([:], '2026-10-10T06:00:00Z')] || ['tasks[0].start: must be at least a minute after the installation start']
        'a task has no group'      | 0    | [ChangeTask.of(details(1, [assignmentGroup: ' ']))] || ['tasks[0].details.assignmentGroup: is required']
        'the change would hold 51' | 49   | [ChangeTask.of(details(1)), ChangeTask.of(details(2))] || ['tasks: CHG0031001 may hold at most 50 change tasks']
    }

    def "an edit is checked against the tasks ProTech holds now and takes the rest of the change from there"() {
        given:
        def stored = raised(tasks: [ctask('CTASK1', 'One', 'First.', OPEN)])
        def current = stored.toBuilder().state(IMPLEMENTATION).version(3L)
                .tasks([ctask('CTASK1', 'One', 'First.', CLOSED)]).build()
        def kept = stored.edited('New', 'Text', schedule(), template(), [ctask('CTASK1', 'One', 'First.', null)],
                NOW)
        def changed = stored.edited('New', 'Text', schedule(), template(),
                [ctask('CTASK1', 'One', 'Changed.', null)], NOW)

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
        changed.rebasedOn(stored).tasks() == [ctask('CTASK1', 'One', 'Changed.', OPEN)]
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

    def "the change tasks are approved only when every active task is approved and there is at least one"() {
        expect:
        raised(tasks: tasks).tasksApproved() == approved

        where:
        tasks                                                                         || approved
        []                                                                            || false
        [task(1).withApproval(APPROVED, [])]                                          || true
        [task(1).withApproval(APPROVED, []), task(2).withApproval(REQUESTED, [])]     || false
        [task(1).withApproval(APPROVED, []), task(2).withApproval(NOT_APPROVED, [])]  || false
        [task(1).withApproval(APPROVED, []), task(2).in(CANCELED)]                    || true
        [task(1).in(CANCELED)]                                                        || false
    }

    def "a reminder to everyone goes to each named approver of the change and of each active task who has not approved yet"() {
        given:
        def change = raised(state: CTASK_APPROVAL, approvals: [
                new ChangeApproval(BUSINESS, 'Rebecca Lawson', APPROVED, null),
                new ChangeApproval(L1, 'Olivia Bennett', REQUESTED, null),
                new ChangeApproval(L2, null, NOT_APPROVED, null),
                new ChangeApproval(SUPPORT, 'Jane Smith', NOT_APPROVED, null)],
                tasks: [task(1).withApproval(APPROVED, ['Ann Lee']), task(2).withApproval(REQUESTED, ['Ann Lee']),
                        ctask('CTASK3', 'Three', 'Third.', CANCELED).withApproval(REQUESTED, ['Ann Lee']),
                        ctask('CTASK4', 'Four', 'Fourth.', OPEN),
                        ctask('CTASK5', 'Five', 'Fifth.', OPEN).withApproval(NOT_APPROVED, ['Grace Turner'])])

        expect:
        change.toRemind(null, null) == [ApprovalRef.of(L1), ApprovalRef.of(SUPPORT), ApprovalRef.ofTask('CTASK0041002'),
                                        ApprovalRef.ofTask('CTASK5')]
        change.toRemind(L1, null) == [ApprovalRef.of(L1)]
        change.toRemind(null, 'CTASK5') == [ApprovalRef.ofTask('CTASK5')]
    }

    def "a reminder is refused when #refusal"() {
        given:
        def change = raised(state: state, approvals: [new ChangeApproval(BUSINESS, 'Rebecca Lawson', APPROVED, null),
                                                      new ChangeApproval(L1, null, REQUESTED, null)],
                tasks: [task(1).withApproval(APPROVED, ['Ann Lee']),
                        ctask('CTASK2', 'Two', 'Second.', CANCELED).withApproval(REQUESTED, ['Ann Lee']),
                        ctask('CTASK3', 'Three', 'Third.', OPEN)])

        when:
        change.toRemind(role, task)

        then:
        def refused = thrown(type)
        refused.message == message

        where:
        refusal                         | state                        | role     | task           || type                    | message
        'the change is closed'          | ChangeState.CLOSED           | null     | null           || IllegalStateException   | 'CHG0031001 is closed in ProTech, so nobody is reminded to approve it'
        'it names a role and a task'    | PRIMARY_APPROVAL             | L1       | 'CTASK3'       || InvalidRequestException | 'Remind the approvers of either the change or one change task'
        'the role already approved'     | PRIMARY_APPROVAL             | BUSINESS | null           || IllegalStateException   | 'The business approver already approved CHG0031001'
        'the role names nobody'         | PRIMARY_APPROVAL             | L1       | null           || IllegalStateException   | 'No L1 approver is named on CHG0031001, so ProTech has nobody to remind'
        'ProTech holds no such role'    | PRIMARY_APPROVAL             | SUPPORT  | null           || IllegalStateException   | 'ProTech has no support approver on CHG0031001'
        'the task is not on the change' | CTASK_APPROVAL               | null     | 'CTASK9'       || InvalidRequestException | 'CTASK9 is not a change task of CHG0031001'
        'the task is canceled'          | CTASK_APPROVAL               | null     | 'CTASK2'       || IllegalStateException   | 'CTASK2 is canceled in ProTech'
        'the task is approved'          | CTASK_APPROVAL               | null     | 'CTASK0041001' || IllegalStateException   | 'CTASK0041001 is already approved'
        'the task names no approvers'   | CTASK_APPROVAL               | null     | 'CTASK3'       || IllegalStateException   | 'ProTech names no approvers of CTASK3 yet'
        'nobody named has to approve'   | CTASK_APPROVAL               | null     | null           || IllegalStateException   | 'Nobody named on CHG0031001 still has to approve it'
    }

    def "the reminders sent are kept on their approvals and survive the next read from ProTech"() {
        given:
        def sent = new Reminder(NOW, ['Olivia Bennett'])
        def toTask = new Reminder(NOW, ['Ann Lee', 'Grace Turner'])
        def change = raised(tasks: [task(1), task(2)])

        when:
        def reminded = change.reminded([(ApprovalRef.of(L1)): sent, (ApprovalRef.ofTask('CTASK0041002')): toTask])
        def synced = reminded.synced(remote(approvals: ChangeApproval.of(template().approvers()).collect {
            new ChangeApproval(it.role(), it.approver(), APPROVED, null)
        }, tasks: raised().tasks().collect { it.withApproval(APPROVED, ['Ann Lee']) }), NOW)

        then:
        reminded.approvals()*.reminder() == [null, sent, null, null]
        reminded.tasks()*.reminder() == [null, toTask]
        reminded.toBuilder().approvals(change.approvals()).tasks(change.tasks()).build() == change
        reminded.unappliedIn(change) == []
        synced.approvals()*.state() == [APPROVED] * 4
        synced.approvals()*.reminder() == [null, sent, null, null]
        synced.tasks()*.approval() == [APPROVED] * 2
        synced.tasks()*.reminder() == [null, toTask]
        reminded.synced(remote(approvals: []), NOW).approvals() == reminded.approvals()
    }

    def "a change approval and a reminder trim their names and keep at most nine people"() {
        expect:
        new ChangeApproval(L1, '  ', null, new Reminder(null, ['Ann'])) == new ChangeApproval(L1, null, NOT_APPROVED, null)
        new ChangeApproval(L1, ' Ann Lee ', REQUESTED, null).approver() == 'Ann Lee'
        new Reminder(NOW, (1..12).collect { " Person $it ".toString() } + [' Person 1', null, ' ']).sentTo() ==
                (1..9).collect { "Person $it".toString() }
        ChangeTask.of(details()).withApproval(REQUESTED, [' Ann ', 'Ann', null]).approvers() == ['Ann']
    }

    static ProductionChange remote(Map changes = [:]) {
        raised([url: 'https://protech/CHG', state: IMPLEMENTATION,
                workflow: [new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120)),
                           new WorkflowStep(IMPLEMENTATION, RAISED.plusSeconds(600))],
                tasks: raised().tasks().collect { it.in(WORK_IN_PROGRESS).withApproval(APPROVED, ['Daniel Foster']) }] + changes)
    }
}

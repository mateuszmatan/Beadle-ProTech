package com.bbh.itss.beadle.protech.adapter.out.protech

import com.bbh.itss.beadle.protech.domain.change.*
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.beadle.protech.domain.shared.RefusedException
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.protech.adapter.out.protech.DemoProTechAdapter.CLOSED_REFUSAL
import static com.bbh.itss.beadle.protech.adapter.out.protech.DemoProTechAdapter.taskStateOf
import static com.bbh.itss.beadle.protech.adapter.out.protech.DemoProTechAdapter.workflowOf
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.*
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.*
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.*
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.DEMO
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CLOSED as TASK_CLOSED
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN
import static com.bbh.itss.beadle.protech.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.*
import static java.time.Duration.*
import static java.time.Instant.MAX

/** The demo ProTech on a clock the spec moves: its numbers, workflow, approvals, updates, adoption and activity. */
class DemoProTechAdapterSpec extends Specification {

    static final ChangeSchedule SHORT_NOTICE = scheduleFrom(CREATED_AT.plus(ofHours(20)))
    static final ChangeSchedule SOON = scheduleFrom(CREATED_AT.plus(ofHours(1)))
    static final ChangeTask CHECK = ctask(null, 'Check the audit trail', 'Open the audit trail.', null)
    static final Instant APPROVED_AT = CREATED_AT.plus(ofMinutes(12))
    static final List<String> ARCHITECTS = DemoApprovers.of('Technology Architecture')

    MutableClock clock = new MutableClock(CREATED_AT)
    def proTech = new DemoProTechAdapter(clock, new DemoProTechProperties(ofSeconds(3)))

    def "each created change gets a new CHG number and each task created on it a new CTASK number"() {
        when:
        def first = proTech.create(change(0))
        def second = proTech.create(change(0))
        def numbers = [proTech.createTask(first.number(), CHECK), proTech.createTask(first.number(), CHECK),
                       proTech.createTask(second.number(), CHECK)]

        then:
        proTech.mode() == DEMO
        first.number() ==~ /CHG\d{7}/
        first.url() == null
        second.number() != first.number()
        numbers.every { it ==~ /CTASK\d{7}/ }
        numbers.toSet().size() == 3
        proTech.read([change(0).numbered(first.number(), null)])[first.number()].tasks()*.number() ==
                numbers.take(2)
    }

    def "a created task is open, named its group's approvers and asked for approval when its change reaches CTask approval"() {
        given:
        def known = created(change(0))
        def number = proTech.createTask(known.number(), CHECK)

        expect:
        ARCHITECTS.size() == 2
        approvals(known, ofMinutes(0)) == [[number, OPEN, NOT_APPROVED, ARCHITECTS]]
        approvals(known, ofMinutes(10).minusSeconds(1)) == [[number, OPEN, NOT_APPROVED, ARCHITECTS]]
        approvals(known, ofMinutes(10)) == [[number, OPEN, REQUESTED, ARCHITECTS]]
        approvals(known, ofMinutes(12)) == [[number, OPEN, APPROVED, ARCHITECTS]]
    }

    def "each approver of the change is asked in the stage of their role and approves when it moves on"() {
        given:
        def known = created(change(1))

        expect:
        changeApprovals(known, after) == states

        where:
        after          || states
        ofSeconds(0)   || [NOT_APPROVED, NOT_APPROVED, NOT_APPROVED, NOT_APPROVED]
        ofMinutes(2)   || [REQUESTED, NOT_APPROVED, NOT_APPROVED, NOT_APPROVED]
        ofMinutes(4)   || [APPROVED, REQUESTED, NOT_APPROVED, NOT_APPROVED]
        ofMinutes(6)   || [APPROVED, APPROVED, REQUESTED, NOT_APPROVED]
        ofMinutes(8)   || [APPROVED, APPROVED, APPROVED, REQUESTED]
        ofMinutes(10)  || [APPROVED, APPROVED, APPROVED, APPROVED]
    }

    def "every change task has its own approvers and approval, and the change is In Progress only once all are approved"() {
        given:
        def known = created(change(0))
        def release = proTech.createTask(known.number(), taskOf(details(1,
                [assignmentGroup: 'Release Management', application: 'CertScanner'])))
        def database = proTech.createTask(known.number(), taskOf(details(2,
                [assignmentGroup: 'Database Administration'])))

        when:
        clock.instant = CREATED_AT.plus(ofMinutes(10))
        def requested = proTech.read([known])[known.number()]
        clock.instant = CREATED_AT.plus(ofMinutes(12))
        def oneApproved = proTech.read([known])[known.number()]
        clock.instant = CREATED_AT.plus(ofMinutes(14))
        def allApproved = proTech.read([known])[known.number()]

        then:
        requested.state() == CTASK_APPROVAL
        requested.tasks().collect { [it.number(), it.approval(), it.approvers()] } == [
                [release, REQUESTED, ['Rebecca Lawson', 'Thomas Ashby']],
                [database, REQUESTED, ['Henry Collins', 'Kenji Watanabe']]]
        oneApproved.state() == CTASK_APPROVAL
        oneApproved.tasks()*.approval() == [APPROVED, REQUESTED]
        allApproved.state() == IMPLEMENTATION
        allApproved.tasks()*.approval() == [APPROVED, APPROVED]
        allApproved.workflow().last() == new WorkflowStep(IMPLEMENTATION, CREATED_AT.plus(ofMinutes(14)))
    }

    def "a change task added to a change In Progress sends it back to CTask approval until the new task is approved"() {
        given:
        def known = created(change(1))
        clock.instant = CREATED_AT.plus(ofMinutes(30))
        def added = proTech.createTask(known.number(), CHECK)

        when:
        def reopened = proTech.read([known])[known.number()]
        clock.instant = CREATED_AT.plus(ofMinutes(34))
        def approved = proTech.read([known])[known.number()]

        then:
        reopened.state() == CTASK_APPROVAL
        reopened.workflow().takeRight(2) == [new WorkflowStep(IMPLEMENTATION, APPROVED_AT),
                                             new WorkflowStep(CTASK_APPROVAL, CREATED_AT.plus(ofMinutes(30)))]
        reopened.tasks().collect { [it.number(), it.approval()] } == [[known.tasks()[0].number(), APPROVED],
                                                                     [added, REQUESTED]]
        reopened.approvals()*.state() == [APPROVED] * 4
        approved.state() == IMPLEMENTATION
        approved.workflow().last() == new WorkflowStep(IMPLEMENTATION, CREATED_AT.plus(ofMinutes(34)))
    }

    def "a change task moved to another assignment group is asked again from the approvers of its new group"() {
        given:
        def known = created(change(1))
        def moved = known.tasks()[0].withDetails(known.tasks()[0].details().toBuilder()
                .assignmentGroup('Database Administration').build())
        clock.instant = CREATED_AT.plus(ofMinutes(30))

        when:
        proTech.update(known.toBuilder().tasks([moved]).build())
        clock.instant = CREATED_AT.plus(ofMinutes(30)).plusSeconds(3)
        def asked = proTech.read([known])[known.number()]
        clock.instant = CREATED_AT.plus(ofMinutes(32)).plusSeconds(3)
        def approved = proTech.read([known])[known.number()]

        then:
        known.tasks()[0].details().assignmentGroup() == 'Technology Architecture'
        asked.state() == CTASK_APPROVAL
        asked.tasks().collect { [it.number(), it.approval(), it.approvers()] } ==
                [[moved.number(), REQUESTED, ['Henry Collins', 'Kenji Watanabe']]]
        approved.state() == IMPLEMENTATION
        approved.tasks()*.approval() == [APPROVED]
    }

    def "ProTech reminds the approver of a role or the approvers of a change task"() {
        given:
        def known = created(change(1))
        clock.instant = CREATED_AT.plus(ofMinutes(5))
        def task = known.tasks()[0].number()

        when:
        def l1 = proTech.remind(known.number(), new ApprovalRef(L1, null))
        def ctask = proTech.remind(known.number(), new ApprovalRef(null, task))

        then:
        l1 == ['Olivia Bennett']
        ctask == ARCHITECTS
    }

    def "ProTech reminds nobody on #refusal"() {
        given:
        def known = created(change(1, SOON).toBuilder().template(issued().template().toBuilder()
                .approvers(new Approvers('Olivia Bennett', null, null, null)).build()).build())
        clock.instant = at

        when:
        proTech.remind(unknown ? 'CHG0000001' : known.number(), approval)

        then:
        def refused = thrown(RefusedException)
        refused.message == message.replace('{CHG}', known.number())

        where:
        refusal                     | at                   | unknown | approval                              || message
        'a closed change'           | SOON.validationEnd() | false   | new ApprovalRef(L1, null)             || CLOSED_REFUSAL
        'a change it does not hold' | CREATED_AT           | true    | new ApprovalRef(L1, null)             || 'ProTech has no change CHG0000001'
        'an unnamed approver'       | CREATED_AT           | false   | new ApprovalRef(SUPPORT, null)        || 'ProTech has nobody to remind on {CHG}'
        'a task it does not hold'   | CREATED_AT           | false   | new ApprovalRef(null, 'CTASK0000001') || 'ProTech has no change task CTASK0000001 on {CHG}'
    }

    def "ProTech refuses to #action #refusal"() {
        given:
        def known = created(change(1, SOON))
        clock.instant = SOON.validationEnd()

        when:
        call(proTech, unknown ? known.toBuilder().number('CHG0000001').build() : known)

        then:
        def refused = thrown(RefusedException)
        refused.message == (unknown ? 'ProTech has no change CHG0000001' : CLOSED_REFUSAL)

        where:
        action               | refusal                     | unknown | call
        'create a task on'   | 'a closed change'           | false   | { p, c -> p.createTask(c.number(), CHECK) }
        'create a task on'   | 'a change it does not hold' | true    | { p, c -> p.createTask(c.number(), CHECK) }
        'change'             | 'a closed change'           | false   | { p, c -> p.update(c) }
        'change'             | 'a change it does not hold' | true    | { p, c -> p.update(c) }
    }

    def "a change planned well ahead whose tasks are approved #approved is in #state #after after it was created"() {
        expect:
        workflowOf(CREATED_AT, schedule(), tasksApproved, CREATED_AT.plus(after))*.state() == states

        where:
        after                        | tasksApproved                   || states
        ofSeconds(0)                 | APPROVED_AT                     || [DRAFT]
        ofMinutes(2)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL]
        ofMinutes(8)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL]
        ofMinutes(10)                | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL]
        ofMinutes(12)                | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofMinutes(12)                | CREATED_AT                      || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofMinutes(30)                | CREATED_AT.plus(ofMinutes(30))  || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122).minusSeconds(1) | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION, CLOSED]
        ofHours(122)                 | MAX                             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL]

        state = states.last()
        approved = tasksApproved == MAX ? 'never' : "at ${tasksApproved}"
    }

    def "a change at short notice is escalated once its tasks are approved and implemented two hours before its installation"() {
        expect:
        workflowOf(CREATED_AT, SHORT_NOTICE, APPROVED_AT, CREATED_AT.plus(ofDays(2))) == [
                new WorkflowStep(DRAFT, CREATED_AT), new WorkflowStep(BUSINESS_APPROVAL, CREATED_AT.plus(ofMinutes(2))),
                new WorkflowStep(PRIMARY_APPROVAL, CREATED_AT.plus(ofMinutes(4))),
                new WorkflowStep(SECONDARY_APPROVAL, CREATED_AT.plus(ofMinutes(6))),
                new WorkflowStep(SUPPORT_APPROVAL, CREATED_AT.plus(ofMinutes(8))),
                new WorkflowStep(CTASK_APPROVAL, CREATED_AT.plus(ofMinutes(10))),
                new WorkflowStep(ESCALATED_APPROVAL, APPROVED_AT),
                new WorkflowStep(IMPLEMENTATION, CREATED_AT.plus(ofHours(18))),
                new WorkflowStep(CLOSED, SHORT_NOTICE.validationEnd())]
        workflowOf(CREATED_AT, SHORT_NOTICE, APPROVED_AT, CREATED_AT.plus(ofHours(17))).last().state() == ESCALATED_APPROVAL
        workflowOf(CREATED_AT, SOON, APPROVED_AT, CREATED_AT.plus(ofMinutes(14)))*.state().takeRight(2) ==
                [ESCALATED_APPROVAL, IMPLEMENTATION]
        workflowOf(CREATED_AT, SOON, APPROVED_AT, CREATED_AT.plus(ofMinutes(13))).last().state() == ESCALATED_APPROVAL
        workflowOf(CREATED_AT, SOON, MAX, CREATED_AT.plus(ofDays(2))).last().state() == CTASK_APPROVAL
    }

    def "a #held task of a change in #state is #expected"() {
        expect:
        taskStateOf(held, state, installing) == expected

        where:
        held             | state           | installing || expected
        CANCELED         | IMPLEMENTATION  | true       || CANCELED
        CANCELED         | CLOSED          | false      || CANCELED
        OPEN             | CLOSED          | false      || TASK_CLOSED
        WORK_IN_PROGRESS | CLOSED          | true       || TASK_CLOSED
        OPEN             | IMPLEMENTATION  | true       || WORK_IN_PROGRESS
        OPEN             | IMPLEMENTATION  | false      || OPEN
        OPEN             | CTASK_APPROVAL  | false      || OPEN
    }

    def "a created change is read back with the workflow and task states of the moment"() {
        given:
        def known = created(change(2))

        when:
        clock.instant = CREATED_AT.plus(ofMinutes(5))
        def approving = proTech.read([known])[known.number()]
        clock.instant = schedule().installationStart().plus(ofMinutes(30))
        def installing = proTech.read([known])[known.number()]
        clock.instant = schedule().validationEnd()
        def closed = proTech.read([known])[known.number()]

        then:
        approving.state() == PRIMARY_APPROVAL
        approving.workflow() == workflowOf(CREATED_AT, schedule(), CREATED_AT.plus(ofMinutes(14)), CREATED_AT.plus(ofMinutes(5)))
        approving.tasks()*.number() == known.tasks()*.number()
        approving.tasks()*.details() == known.tasks()*.details()
        approving.tasks().collect { [it.approval(), it.approvers()] } == [[NOT_APPROVED, ARCHITECTS]] * 2
        approving.approvals() == [new ChangeApproval(BUSINESS, 'Rebecca Lawson', APPROVED, null),
                                  new ChangeApproval(L1, 'Olivia Bennett', REQUESTED, null),
                                  new ChangeApproval(L2, 'James Carter', NOT_APPROVED, null),
                                  new ChangeApproval(SUPPORT, 'Jane Smith', NOT_APPROVED, null)]
        approving.toBuilder().state(DRAFT).workflow([]).tasks(known.tasks()).approvals(known.approvals()).build() ==
                known
        installing.state() == IMPLEMENTATION
        installing.tasks()*.state() == [WORK_IN_PROGRESS] * 2
        installing.tasks()*.approval() == [APPROVED] * 2
        closed.state() == CLOSED
        closed.tasks()*.state() == [TASK_CLOSED] * 2
    }

    def "an update is applied after the apply delay, numbering new tasks and canceling the dropped ones"() {
        given:
        def known = created(change(2))
        def later = scheduleFrom(schedule().installationStart().plus(ofDays(1)))
        def requested = known.toBuilder().shortDescription('Renamed').description('New text').schedule(later)
                .template(known.template().toBuilder().category('Apps').jiraProjectKey('OTHER').build())
                .tasks([known.tasks()[1], CHECK]).build()
        clock.instant = CREATED_AT.plus(ofMinutes(1))

        when:
        proTech.update(requested)
        def before = proTech.read([known])[known.number()]
        clock.instant = CREATED_AT.plus(ofMinutes(1)).plusSeconds(3)
        def after = proTech.read([known])[known.number()]
        def again = proTech.read([known])[known.number()]

        then:
        before.shortDescription() == known.shortDescription()
        before.tasks()*.number() == known.tasks()*.number()
        before.tasks()*.details() == known.tasks()*.details()
        after.shortDescription() == 'Renamed'
        after.description() == 'New text'
        after.schedule() == later
        after.template().category() == 'Apps'
        after.template().jiraProjectKey() == 'CERT'
        after.tasks()*.details()*.shortDescription() == [known.tasks()[1], CHECK, known.tasks()[0]]
                *.details()*.shortDescription()
        after.tasks()*.number()[0] == known.tasks()[1].number()
        after.tasks()*.number()[1] ==~ /CTASK\d{7}/
        after.tasks()*.number()[2] == known.tasks()[0].number()
        after.tasks()*.state() == [OPEN, OPEN, CANCELED]
        again == after
    }

    def "a canceled task keeps its text when an update sends it again"() {
        given:
        def known = created(change(2))
        proTech.update(known.toBuilder().tasks([known.tasks()[0]]).build())
        clock.instant = CREATED_AT.plusSeconds(3)
        def canceled = known.tasks()[1]

        when:
        proTech.update(known.toBuilder().tasks([known.tasks()[0], ctask(canceled.number(), 'Back again',
                'Back again.', OPEN)]).build())
        clock.instant = CREATED_AT.plusSeconds(6)
        def after = proTech.read([known])[known.number()]

        then:
        after.tasks().collect { [it.number(), it.details(), it.state()] } == [
                [known.tasks()[0].number(), known.tasks()[0].details(), OPEN],
                [canceled.number(), canceled.details(), CANCELED]]
    }

    def "a schedule change is not applied once the installation has started, the rest is"() {
        given:
        def known = created(change(1, SOON))
        def moved = scheduleFrom(SOON.installationStart().plus(ofHours(2)))
        clock.instant = SOON.installationStart().plus(ofMinutes(10))
        def requested = known.toBuilder().shortDescription('Renamed').schedule(moved).build()

        when:
        proTech.update(requested)
        clock.instant = SOON.installationStart().plus(ofMinutes(11))
        def after = proTech.read([known])[known.number()]

        then:
        after.state() == IMPLEMENTATION
        after.shortDescription() == 'Renamed'
        after.schedule() == SOON
    }

    def "a rescheduled change keeps the stages it reached and enters the next ones no earlier than the new schedule"() {
        given:
        def implementing = created(change(1))
        def escalated = created(change(1, SHORT_NOTICE))
        clock.instant = CREATED_AT.plus(ofMinutes(30))
        def reached = proTech.read([implementing, escalated])

        when:
        proTech.update(implementing.toBuilder().schedule(scheduleFrom(CREATED_AT.plus(ofHours(6)))).build())
        proTech.update(escalated.toBuilder().schedule(scheduleFrom(CREATED_AT.plus(ofHours(1)))).build())
        clock.instant = CREATED_AT.plus(ofMinutes(31))
        def after = proTech.read([implementing, escalated])

        then:
        reached[implementing.number()].state() == IMPLEMENTATION
        reached[escalated.number()].state() == ESCALATED_APPROVAL
        after[implementing.number()].workflow() == reached[implementing.number()].workflow()
        after[escalated.number()].workflow() == reached[escalated.number()].workflow() +
                new WorkflowStep(IMPLEMENTATION, CREATED_AT.plus(ofMinutes(30)).plusSeconds(3))
    }

    def "a change ProTech does not hold, as after a restart, is adopted from the portal's copy"() {
        given:
        def known = issued(tasks: issued().tasks() + CHECK)
        clock.instant = CREATED_AT.plus(ofMinutes(3))

        when:
        def copy = proTech.read([known])[known.number()]
        def again = proTech.read([known])[known.number()]

        then:
        copy.state() == BUSINESS_APPROVAL
        copy.workflow() == workflowOf(CREATED_AT, known.schedule(), MAX, CREATED_AT.plus(ofMinutes(3)))
        copy.tasks()*.number().take(2) == ['CTASK0041001', 'CTASK0041002']
        copy.tasks()[2].number() ==~ /CTASK\d{7}/
        copy.tasks()*.details() == known.tasks()*.details()
        again.tasks() == copy.tasks()
    }

    def "a change without a creation time or a workflow is adopted as created now"() {
        given:
        def known = issued(createdAt: null, workflow: [])
        clock.instant = CREATED_AT.plus(ofHours(1))

        expect:
        proTech.read([known])[known.number()].workflow() == [new WorkflowStep(DRAFT, CREATED_AT.plus(ofHours(1)))]
    }

    def "the demo tells what moved since a time: the stage, the task approvals and the installation"() {
        given:
        def known = created(change(0))
        def number = known.number()
        proTech.createTask(number, CHECK)
        def installation = known.schedule().installationStart()

        expect:
        activity(number, ofMinutes(1), CREATED_AT) == null
        activity(number, ofMinutes(1), CREATED_AT.minusSeconds(1)) == new ChangeActivity(number, DRAFT, CREATED_AT, null, null)
        activity(number, ofMinutes(3), CREATED_AT) ==
                new ChangeActivity(number, BUSINESS_APPROVAL, CREATED_AT.plus(ofMinutes(2)), null, null)
        activity(number, ofMinutes(11), CREATED_AT.plus(ofMinutes(9))) ==
                new ChangeActivity(number, CTASK_APPROVAL, CREATED_AT.plus(ofMinutes(10)), CREATED_AT.plus(ofMinutes(10)), null)
        activity(number, ofMinutes(13), CREATED_AT.plus(ofMinutes(11))) ==
                new ChangeActivity(number, IMPLEMENTATION, APPROVED_AT, APPROVED_AT, null)
        activity(number, between(CREATED_AT, installation), installation.minusSeconds(1)) ==
                new ChangeActivity(number, IMPLEMENTATION, APPROVED_AT, APPROVED_AT, installation)
        proTech.activity([number, 'CHG0000001'], null).keySet() == [number] as Set
    }

    def "a change the demo got late, created or adopted after a restart, moved no earlier than when it got it"() {
        given:
        clock.instant = CREATED_AT.plus(ofMinutes(got))
        def number = adopted ? proTech.read([issued(tasks: [], workflow: [new WorkflowStep(DRAFT, CREATED_AT)])]).keySet()[0]
                : proTech.create(change(0).toBuilder().createdAt(CREATED_AT).build()).number()

        expect:
        proTech.activity([number], CREATED_AT.plus(ofMinutes(got - 1))) ==
                [(number): new ChangeActivity(number, state, CREATED_AT.plus(ofMinutes(got)), null, null)]
        proTech.activity([number], CREATED_AT.plus(ofMinutes(got))) == [:]

        where:
        adopted | got || state
        false   | 30  || CTASK_APPROVAL
        true    | 5   || PRIMARY_APPROVAL
    }

    private ChangeActivity activity(String number, Duration after, Instant since) {
        clock.instant = CREATED_AT.plus(after)
        proTech.activity([number], since)[number]
    }

    /** The change as the demo holds it once it created the change and then each of its tasks. */
    private ProductionChange created(ProductionChange change) {
        def bare = change.toBuilder().tasks([]).build()
        def answer = proTech.create(bare)
        bare.numbered(answer.number(), answer.url())
                .withTasks(change.tasks().collect { it.withNumber(proTech.createTask(answer.number(), it)) })
    }

    private List approvals(ProductionChange known, Duration after) {
        clock.instant = CREATED_AT.plus(after)
        proTech.read([known])[known.number()].tasks().collect {
            [it.number(), it.state(), it.approval(), it.approvers()]
        }
    }

    private List changeApprovals(ProductionChange known, Duration after) {
        clock.instant = CREATED_AT.plus(after)
        proTech.read([known])[known.number()].approvals()*.state()
    }

    static ProductionChange change(int tasks, ChangeSchedule schedule = schedule()) {
        issued(id: null, number: null, schedule: schedule, createdAt: null, workflow: [], syncedAt: null,
                tasks: (0..<tasks).collect { ctask(null, "Task ${it + 1}", "Step ${it + 1}.", null) })
    }

    static ChangeTask taskOf(TaskDetails details) {
        new ChangeTask(null, details, null, null, null, null, null)
    }

    static ChangeSchedule scheduleFrom(Instant start) {
        new ChangeSchedule(start, start.plus(ofHours(2)), start.plus(ofHours(2)), start.plus(ofHours(3)),
                start.plus(ofHours(10)), null, null)
    }
}

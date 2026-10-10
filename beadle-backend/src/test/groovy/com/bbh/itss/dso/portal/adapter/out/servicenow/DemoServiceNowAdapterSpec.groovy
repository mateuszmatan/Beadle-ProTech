package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.domain.change.ApprovalRef
import com.bbh.itss.dso.portal.domain.change.ChangeApproval
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.CLOSED_REFUSAL
import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.taskStateOf
import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.workflowOf
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L1
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L2
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.SUPPORT
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.ESCALATED_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SUPPORT_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED as TASK_CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.ctask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.details
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static java.time.Duration.ofDays
import static java.time.Duration.ofHours
import static java.time.Duration.ofMinutes
import static java.time.Duration.ofSeconds
import static java.time.Instant.MAX

class DemoServiceNowAdapterSpec extends Specification {

    static final ChangeSchedule SHORT_NOTICE = scheduleFrom(RAISED.plus(ofHours(20)))
    static final ChangeSchedule SOON = scheduleFrom(RAISED.plus(ofHours(1)))
    static final ChangeTask CHECK = ctask(null, 'Check the audit trail', 'Open the audit trail.', null)
    static final Instant APPROVED_AT = RAISED.plus(ofMinutes(12))
    static final List<String> ARCHITECTS = DemoApprovers.of('Technology Architecture')

    MutableClock clock = new MutableClock(RAISED)
    def serviceNow = new DemoServiceNowAdapter(clock, new DemoProTechProperties(ofSeconds(3)))

    def "each raised change gets a new CHG number and each task created on it a new CTASK number"() {
        when:
        def first = serviceNow.raise(change(0))
        def second = serviceNow.raise(change(0))
        def numbers = [serviceNow.createTask(first.number(), CHECK), serviceNow.createTask(first.number(), CHECK),
                       serviceNow.createTask(second.number(), CHECK)]

        then:
        !serviceNow.connected()
        first.number() ==~ /CHG\d{7}/
        first.url() == null
        second.number() != first.number()
        numbers.every { it ==~ /CTASK\d{7}/ }
        numbers.toSet().size() == 3
        serviceNow.read([change(0).numbered(first.number(), null)])[first.number()].tasks()*.number() ==
                numbers.take(2)
    }

    def "a created task is open, named its group's approvers and asked for approval when its change reaches CTask approval"() {
        given:
        def known = raise(change(0))
        def number = serviceNow.createTask(known.number(), CHECK)

        expect:
        ARCHITECTS.size() == 2
        approvals(known, ofMinutes(0)) == [[number, OPEN, NOT_APPROVED, ARCHITECTS]]
        approvals(known, ofMinutes(10).minusSeconds(1)) == [[number, OPEN, NOT_APPROVED, ARCHITECTS]]
        approvals(known, ofMinutes(10)) == [[number, OPEN, REQUESTED, ARCHITECTS]]
        approvals(known, ofMinutes(12)) == [[number, OPEN, APPROVED, ARCHITECTS]]
    }

    def "each approver of the change is asked in the stage of their role and approves when it moves on"() {
        given:
        def known = raise(change(1))

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
        def known = raise(change(0))
        def release = serviceNow.createTask(known.number(), ChangeTask.of(details(1,
                [assignmentGroup: 'Release Management', application: 'CertScanner'])))
        def database = serviceNow.createTask(known.number(), ChangeTask.of(details(2,
                [assignmentGroup: 'Database Administration'])))

        when:
        clock.instant = RAISED.plus(ofMinutes(10))
        def requested = serviceNow.read([known])[known.number()]
        clock.instant = RAISED.plus(ofMinutes(12))
        def oneApproved = serviceNow.read([known])[known.number()]
        clock.instant = RAISED.plus(ofMinutes(14))
        def allApproved = serviceNow.read([known])[known.number()]

        then:
        requested.state() == CTASK_APPROVAL
        requested.tasks().collect { [it.number(), it.approval(), it.approvers()] } == [
                [release, REQUESTED, ['Rebecca Lawson', 'Thomas Ashby']],
                [database, REQUESTED, ['Henry Collins', 'Kenji Watanabe']]]
        oneApproved.state() == CTASK_APPROVAL
        oneApproved.tasks()*.approval() == [APPROVED, REQUESTED]
        !oneApproved.tasksApproved()
        allApproved.state() == IMPLEMENTATION
        allApproved.tasks()*.approval() == [APPROVED, APPROVED]
        allApproved.tasksApproved()
        allApproved.workflow().last() == new WorkflowStep(IMPLEMENTATION, RAISED.plus(ofMinutes(14)))
    }

    def "a change without change tasks waits in CTask approval"() {
        given:
        def known = raise(change(0))
        clock.instant = schedule().validationEnd().plus(ofDays(1))

        expect:
        serviceNow.read([known])[known.number()].state() == CTASK_APPROVAL
    }

    def "a change task added to a change In Progress sends it back to CTask approval until the new task is approved"() {
        given:
        def known = raise(change(1))
        clock.instant = RAISED.plus(ofMinutes(30))
        def added = serviceNow.createTask(known.number(), CHECK)

        when:
        def reopened = serviceNow.read([known])[known.number()]
        clock.instant = RAISED.plus(ofMinutes(34))
        def approved = serviceNow.read([known])[known.number()]

        then:
        reopened.state() == CTASK_APPROVAL
        reopened.workflow().takeRight(2) == [new WorkflowStep(IMPLEMENTATION, APPROVED_AT),
                                             new WorkflowStep(CTASK_APPROVAL, RAISED.plus(ofMinutes(30)))]
        reopened.tasks().collect { [it.number(), it.approval()] } == [[known.tasks()[0].number(), APPROVED],
                                                                     [added, REQUESTED]]
        reopened.approvals()*.state() == [APPROVED] * 4
        approved.state() == IMPLEMENTATION
        approved.workflow().last() == new WorkflowStep(IMPLEMENTATION, RAISED.plus(ofMinutes(34)))
    }

    def "ProTech reminds the approver of a role or the approvers of a change task and records whom it reminded"() {
        given:
        def known = raise(change(1))
        clock.instant = RAISED.plus(ofMinutes(5))
        def task = known.tasks()[0].number()

        when:
        def l1 = serviceNow.remind(known.number(), ApprovalRef.of(L1))
        def ctask = serviceNow.remind(known.number(), ApprovalRef.ofTask(task))

        then:
        l1 == ['Olivia Bennett']
        ctask == ARCHITECTS
        serviceNow.sent() == [
                new DemoServiceNowAdapter.SentReminder(known.number(), ApprovalRef.of(L1), l1, clock.instant()),
                new DemoServiceNowAdapter.SentReminder(known.number(), ApprovalRef.ofTask(task), ctask,
                        clock.instant())]
    }

    def "ProTech reminds nobody on #refusal"() {
        given:
        def known = raise(change(1, SOON).toBuilder().template(raised().template().toBuilder()
                .approvers(new Approvers('Olivia Bennett', null, null, null)).build()).build())
        clock.instant = at

        when:
        serviceNow.remind(unknown ? 'CHG0000001' : known.number(), approval)

        then:
        def refused = thrown(IllegalStateException)
        refused.message == message.replace('{CHG}', known.number())

        where:
        refusal                     | at                   | unknown | approval                           || message
        'a closed change'           | SOON.validationEnd() | false   | ApprovalRef.of(L1)                 || CLOSED_REFUSAL
        'a change it does not hold' | RAISED               | true    | ApprovalRef.of(L1)                 || 'ProTech has no change CHG0000001'
        'an unnamed approver'       | RAISED               | false   | ApprovalRef.of(SUPPORT)            || 'ProTech has nobody to remind on {CHG}'
        'a task it does not hold'   | RAISED               | false   | ApprovalRef.ofTask('CTASK0000001') || 'ProTech has no change task CTASK0000001 on {CHG}'
    }

    def "ProTech creates no task on #refusal"() {
        given:
        def known = raise(change(1, SOON))
        clock.instant = SOON.validationEnd()

        when:
        serviceNow.createTask(unknown ? 'CHG0000001' : known.number(), CHECK)

        then:
        def refused = thrown(IllegalStateException)
        refused.message == message

        where:
        refusal                     | unknown || message
        'a closed change'           | false   || CLOSED_REFUSAL
        'a change it does not hold' | true    || 'ProTech has no change CHG0000001'
    }

    def "a change planned well ahead whose tasks are approved #approved is in #state #after after it was raised"() {
        expect:
        workflowOf(RAISED, schedule(), tasksApproved, RAISED.plus(after))*.state() == states

        where:
        after                        | tasksApproved                   || states
        ofSeconds(0)                 | APPROVED_AT                     || [DRAFT]
        ofSeconds(119)               | APPROVED_AT                     || [DRAFT]
        ofMinutes(2)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL]
        ofMinutes(4)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL]
        ofMinutes(6)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL]
        ofMinutes(8)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL]
        ofMinutes(10)                | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL]
        ofMinutes(12)                | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofMinutes(12)                | RAISED                          || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofMinutes(29)                | RAISED.plus(ofMinutes(30))      || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL]
        ofMinutes(30)                | RAISED.plus(ofMinutes(30))      || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122).minusSeconds(1) | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122)                 | APPROVED_AT                     || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION, CLOSED]
        ofHours(122)                 | MAX                             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL]

        state = states.last()
        approved = tasksApproved == MAX ? 'never' : "at ${tasksApproved}"
    }

    def "a change at short notice is escalated once its tasks are approved and implemented two hours before its installation"() {
        expect:
        workflowOf(RAISED, SHORT_NOTICE, APPROVED_AT, RAISED.plus(ofDays(2))) == [
                new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, RAISED.plus(ofMinutes(2))),
                new WorkflowStep(PRIMARY_APPROVAL, RAISED.plus(ofMinutes(4))),
                new WorkflowStep(SECONDARY_APPROVAL, RAISED.plus(ofMinutes(6))),
                new WorkflowStep(SUPPORT_APPROVAL, RAISED.plus(ofMinutes(8))),
                new WorkflowStep(CTASK_APPROVAL, RAISED.plus(ofMinutes(10))),
                new WorkflowStep(ESCALATED_APPROVAL, APPROVED_AT),
                new WorkflowStep(IMPLEMENTATION, RAISED.plus(ofHours(18))),
                new WorkflowStep(CLOSED, SHORT_NOTICE.validationEnd())]
        workflowOf(RAISED, SHORT_NOTICE, APPROVED_AT, RAISED.plus(ofHours(17))).last().state() == ESCALATED_APPROVAL
        workflowOf(RAISED, SOON, APPROVED_AT, RAISED.plus(ofMinutes(14)))*.state().takeRight(2) ==
                [ESCALATED_APPROVAL, IMPLEMENTATION]
        workflowOf(RAISED, SOON, APPROVED_AT, RAISED.plus(ofMinutes(13))).last().state() == ESCALATED_APPROVAL
        workflowOf(RAISED, SOON, MAX, RAISED.plus(ofDays(2))).last().state() == CTASK_APPROVAL
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

    def "a raised change is read back with the workflow and task states of the moment"() {
        given:
        def known = raise(change(2))

        when:
        clock.instant = RAISED.plus(ofMinutes(5))
        def approving = serviceNow.read([known])[known.number()]
        clock.instant = schedule().installationStart().plus(ofMinutes(30))
        def installing = serviceNow.read([known])[known.number()]
        clock.instant = schedule().validationEnd()
        def closed = serviceNow.read([known])[known.number()]

        then:
        approving.state() == PRIMARY_APPROVAL
        approving.workflow() == workflowOf(RAISED, schedule(), RAISED.plus(ofMinutes(14)), RAISED.plus(ofMinutes(5)))
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
        def known = raise(change(2))
        def later = scheduleFrom(schedule().installationStart().plus(ofDays(1)))
        def requested = known.toBuilder().shortDescription('Renamed').description('New text').schedule(later)
                .template(known.template().toBuilder().category('Apps').jiraProjectKey('OTHER').build())
                .tasks([known.tasks()[1], CHECK]).build()
        clock.instant = RAISED.plus(ofMinutes(1))

        when:
        serviceNow.update(requested)
        def before = serviceNow.read([known])[known.number()]
        clock.instant = RAISED.plus(ofMinutes(1)).plusSeconds(3)
        def after = serviceNow.read([known])[known.number()]
        def again = serviceNow.read([known])[known.number()]

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
        requested.unappliedIn(after) == []
        again == after
    }

    def "a canceled task keeps its text when an update sends it again"() {
        given:
        def known = raise(change(2))
        serviceNow.update(known.toBuilder().tasks([known.tasks()[0]]).build())
        clock.instant = RAISED.plusSeconds(3)
        def canceled = known.tasks()[1]

        when:
        serviceNow.update(known.toBuilder().tasks([known.tasks()[0], ctask(canceled.number(), 'Back again',
                'Back again.', OPEN)]).build())
        clock.instant = RAISED.plusSeconds(6)
        def after = serviceNow.read([known])[known.number()]

        then:
        after.tasks().collect { [it.number(), it.details(), it.state()] } == [
                [known.tasks()[0].number(), known.tasks()[0].details(), OPEN],
                [canceled.number(), canceled.details(), CANCELED]]
    }

    def "a schedule change is not applied once the installation has started, the rest is"() {
        given:
        def known = raise(change(1, SOON))
        def moved = scheduleFrom(SOON.installationStart().plus(ofHours(2)))
        clock.instant = SOON.installationStart().plus(ofMinutes(10))
        def requested = known.toBuilder().shortDescription('Renamed').schedule(moved).build()

        when:
        serviceNow.update(requested)
        clock.instant = SOON.installationStart().plus(ofMinutes(11))
        def after = serviceNow.read([known])[known.number()]

        then:
        after.state() == IMPLEMENTATION
        after.shortDescription() == 'Renamed'
        after.schedule() == SOON
        requested.unappliedIn(after) == ['schedule.installationStart', 'schedule.installationEnd',
                                         'schedule.validationStart', 'schedule.validationEnd', 'schedule.firstUsage']
    }

    def "a rescheduled change keeps the stages it reached and enters the next ones no earlier than the new schedule"() {
        given:
        def implementing = raise(change(1))
        def escalated = raise(change(1, SHORT_NOTICE))
        clock.instant = RAISED.plus(ofMinutes(30))
        def reached = serviceNow.read([implementing, escalated])

        when:
        serviceNow.update(implementing.toBuilder().schedule(scheduleFrom(RAISED.plus(ofHours(6)))).build())
        serviceNow.update(escalated.toBuilder().schedule(scheduleFrom(RAISED.plus(ofHours(1)))).build())
        clock.instant = RAISED.plus(ofMinutes(31))
        def after = serviceNow.read([implementing, escalated])

        then:
        reached[implementing.number()].state() == IMPLEMENTATION
        reached[escalated.number()].state() == ESCALATED_APPROVAL
        after[implementing.number()].workflow() == reached[implementing.number()].workflow()
        after[escalated.number()].workflow() == reached[escalated.number()].workflow() +
                new WorkflowStep(IMPLEMENTATION, RAISED.plus(ofMinutes(30)).plusSeconds(3))
    }

    def "ProTech refuses to change #refusal"() {
        given:
        def known = raise(change(1, SOON))
        clock.instant = SOON.validationEnd()

        when:
        serviceNow.update(unknown ? known.toBuilder().number('CHG0000001').build() : known)

        then:
        def refused = thrown(IllegalStateException)
        refused.message == message

        where:
        refusal           | unknown || message
        'a closed change' | false   || CLOSED_REFUSAL
        'a change it does not hold' | true || 'ProTech has no change CHG0000001'
    }

    def "a change ProTech does not hold, as after a restart, is adopted from the portal's copy"() {
        given:
        def known = raised(tasks: raised().tasks() + CHECK)
        clock.instant = RAISED.plus(ofMinutes(3))

        when:
        def copy = serviceNow.read([known])[known.number()]
        def again = serviceNow.read([known])[known.number()]

        then:
        copy.state() == BUSINESS_APPROVAL
        copy.workflow() == workflowOf(RAISED, known.schedule(), MAX, RAISED.plus(ofMinutes(3)))
        copy.tasks()*.number().take(2) == ['CTASK0041001', 'CTASK0041002']
        copy.tasks()[2].number() ==~ /CTASK\d{7}/
        known.unappliedIn(copy) == []
        again.tasks() == copy.tasks()
    }

    def "a rescheduled change adopted by a restarted ProTech keeps the stages it reached and goes on from there"() {
        given:
        def implementing = raise(change(1))
        clock.instant = RAISED.plus(ofMinutes(30))
        def moved = scheduleFrom(RAISED.plus(ofHours(6)))
        serviceNow.update(implementing.toBuilder().schedule(moved).build())
        clock.instant = RAISED.plus(ofMinutes(31))
        def copy = serviceNow.read([implementing])[implementing.number()]
        def restarted = new DemoServiceNowAdapter(clock, new DemoProTechProperties(ofSeconds(3)))

        when:
        clock.instant = RAISED.plus(ofHours(1))
        def adopted = restarted.read([copy])[copy.number()]
        clock.instant = moved.validationEnd()
        def closed = restarted.read([copy])[copy.number()]

        then:
        copy.schedule() == moved
        copy.state() == IMPLEMENTATION
        workflowOf(RAISED, moved, APPROVED_AT, RAISED.plus(ofHours(1))).last().state() == ESCALATED_APPROVAL
        adopted.workflow() == copy.workflow()
        adopted.state() == IMPLEMENTATION
        closed.workflow() == copy.workflow() + new WorkflowStep(CLOSED, moved.validationEnd())
    }

    def "a change without a raise time or a workflow is adopted as raised now"() {
        given:
        def known = raised(createdAt: null, workflow: [])
        clock.instant = RAISED.plus(ofHours(1))

        expect:
        serviceNow.read([known])[known.number()].workflow() == [new WorkflowStep(DRAFT, RAISED.plus(ofHours(1)))]
    }

    private ProductionChange raise(ProductionChange change) {
        def bare = change.toBuilder().tasks([]).build()
        def raised = serviceNow.raise(bare)
        bare.numbered(raised.number(), raised.url())
                .withTasks(change.tasks().collect { it.numbered(serviceNow.createTask(raised.number(), it)) })
    }

    private List approvals(ProductionChange known, Duration after) {
        clock.instant = RAISED.plus(after)
        serviceNow.read([known])[known.number()].tasks().collect {
            [it.number(), it.state(), it.approval(), it.approvers()]
        }
    }

    private List changeApprovals(ProductionChange known, Duration after) {
        clock.instant = RAISED.plus(after)
        serviceNow.read([known])[known.number()].approvals()*.state()
    }

    static ProductionChange change(int tasks, ChangeSchedule schedule = schedule()) {
        raised(id: null, number: null, schedule: schedule, createdAt: null, workflow: [], syncedAt: null,
                tasks: (0..<tasks).collect { ctask(null, "Task ${it + 1}", "Step ${it + 1}.", null) })
    }

    static ChangeSchedule scheduleFrom(Instant start) {
        new ChangeSchedule(start, start.plus(ofHours(2)), start.plus(ofHours(2)), start.plus(ofHours(3)),
                start.plus(ofHours(10)), null, null)
    }
}

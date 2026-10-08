package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import spock.lang.Specification
import spock.util.time.MutableClock

import java.time.Instant

import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.CLOSED_REFUSAL
import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.taskStateOf
import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoServiceNowAdapter.workflowOf
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.ESCALATED_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED as TASK_CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static java.time.Duration.ofDays
import static java.time.Duration.ofHours
import static java.time.Duration.ofMinutes
import static java.time.Duration.ofSeconds

class DemoServiceNowAdapterSpec extends Specification {

    static final ChangeSchedule SHORT_NOTICE = scheduleFrom(RAISED.plus(ofHours(20)))
    static final ChangeSchedule SOON = scheduleFrom(RAISED.plus(ofHours(1)))
    static final ChangeTask CHECK = new ChangeTask(null, 'Check the audit trail', 'Open the audit trail.', null)

    MutableClock clock = new MutableClock(RAISED)
    def serviceNow = new DemoServiceNowAdapter(clock, ofSeconds(3))

    def "each raised change gets a new CHG number and a new CTASK number per task"() {
        when:
        def first = serviceNow.raise(change(2))
        def second = serviceNow.raise(change(1))

        then:
        !serviceNow.connected()
        first.number() ==~ /CHG\d{7}/
        first.taskNumbers().size() == 2
        first.taskNumbers().every { it ==~ /CTASK\d{7}/ }
        first.url() == null
        second.number() != first.number()
        second.taskNumbers().size() == 1
        !first.taskNumbers().contains(second.taskNumbers()[0])
    }

    def "a change planned well ahead is in #state #after after it was raised"() {
        expect:
        workflowOf(RAISED, schedule(), RAISED.plus(after))*.state() == states
        workflowOf(RAISED, schedule(), RAISED.plus(after)).last().state() == state

        where:
        after                    || states
        ofSeconds(0)             || [DRAFT]
        ofSeconds(119)           || [DRAFT]
        ofMinutes(2)             || [DRAFT, BUSINESS_APPROVAL]
        ofMinutes(4)             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL]
        ofMinutes(6)             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL]
        ofMinutes(8)             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, CTASK_APPROVAL]
        ofMinutes(10)            || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122).minusSeconds(1) || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION]
        ofHours(122)             || [DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, CTASK_APPROVAL, IMPLEMENTATION, CLOSED]

        state = states.last()
    }

    def "a change at short notice is escalated and implemented two hours before its installation"() {
        expect:
        workflowOf(RAISED, SHORT_NOTICE, RAISED.plus(ofDays(2))) == [
                new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, RAISED.plus(ofMinutes(2))),
                new WorkflowStep(PRIMARY_APPROVAL, RAISED.plus(ofMinutes(4))),
                new WorkflowStep(SECONDARY_APPROVAL, RAISED.plus(ofMinutes(6))),
                new WorkflowStep(CTASK_APPROVAL, RAISED.plus(ofMinutes(8))),
                new WorkflowStep(ESCALATED_APPROVAL, RAISED.plus(ofMinutes(10))),
                new WorkflowStep(IMPLEMENTATION, RAISED.plus(ofHours(18))),
                new WorkflowStep(CLOSED, SHORT_NOTICE.validationEnd())]
        workflowOf(RAISED, SHORT_NOTICE, RAISED.plus(ofHours(17))).last().state() == ESCALATED_APPROVAL
        workflowOf(RAISED, SOON, RAISED.plus(ofMinutes(12)))*.state().takeRight(2) ==
                [ESCALATED_APPROVAL, IMPLEMENTATION]
        workflowOf(RAISED, SOON, RAISED.plus(ofMinutes(11))).last().state() == ESCALATED_APPROVAL
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
        approving.workflow() == workflowOf(RAISED, schedule(), RAISED.plus(ofMinutes(5)))
        approving.tasks() == known.tasks()
        approving.toBuilder().state(DRAFT).workflow([]).build() == known
        installing.state() == IMPLEMENTATION
        installing.tasks()*.state() == [WORK_IN_PROGRESS] * 2
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
        before.tasks() == known.tasks()
        after.shortDescription() == 'Renamed'
        after.description() == 'New text'
        after.schedule() == later
        after.template().category() == 'Apps'
        after.template().jiraProjectKey() == 'CERT'
        after.tasks()*.shortDescription() == [known.tasks()[1].shortDescription(), CHECK.shortDescription(),
                                              known.tasks()[0].shortDescription()]
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
        serviceNow.update(known.toBuilder().tasks([known.tasks()[0], new ChangeTask(canceled.number(), 'Back again',
                'Back again.', OPEN)]).build())
        clock.instant = RAISED.plusSeconds(6)
        def after = serviceNow.read([known])[known.number()]

        then:
        after.tasks() == [known.tasks()[0], canceled.in(CANCELED)]
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
        copy.workflow() == workflowOf(RAISED, known.schedule(), RAISED.plus(ofMinutes(3)))
        copy.tasks()*.number().take(2) == ['CTASK0041001', 'CTASK0041002']
        copy.tasks()[2].number() ==~ /CTASK\d{7}/
        known.unappliedIn(copy) == []
        again.tasks() == copy.tasks()
    }

    def "a change without a raise time is adopted as raised now"() {
        given:
        def known = raised(createdAt: null)
        clock.instant = RAISED.plus(ofHours(1))

        expect:
        serviceNow.read([known])[known.number()].workflow() == [new WorkflowStep(DRAFT, RAISED.plus(ofHours(1)))]
    }

    private ProductionChange raise(ProductionChange change) {
        def raised = serviceNow.raise(change)
        change.numbered(raised.number(), raised.taskNumbers(), raised.url())
    }

    static ProductionChange change(int tasks, ChangeSchedule schedule = schedule()) {
        raised(id: null, number: null, schedule: schedule, createdAt: null, workflow: [], syncedAt: null,
                tasks: (1..tasks).collect { new ChangeTask(null, "Task $it", "Step $it.", null) })
    }

    static ChangeSchedule scheduleFrom(Instant start) {
        new ChangeSchedule(start, start.plus(ofHours(2)), start.plus(ofHours(2)), start.plus(ofHours(3)),
                start.plus(ofHours(10)))
    }
}

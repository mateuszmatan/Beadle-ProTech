package com.bbh.itss.beadle.protech.regression

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification
import com.bbh.itss.beadle.protech.support.Json

import java.time.Instant

import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static java.time.Duration.ofDays
import static java.time.Duration.ofMinutes
import static java.time.Duration.ofSeconds

class ChangeRegressionSpec extends BeadleProTechSpecification {

    static final String SUMMARY = 'CertScanner CERT 4.2: Expiry alerts'

    def "a change created with its tasks is read back moving through the approvals as the clock advances"() {
        given:
        Instant start = clock.instant()
        def draft = draft(start, start + ofDays(3))

        when: 'Beadle-GUI creates the change and its two tasks'
        String number = create(draft)
        List<String> tasks = [createTask(number, 1), createTask(number, 2)]
        def known = draft.toBuilder().number(number).build()
        def created = read(known)

        then: 'ProTech holds a draft with both tasks open and nothing requested yet'
        number ==~ /CHG\d{7}/
        tasks.every { it ==~ /CTASK\d{7}/ } && tasks.unique(false).size() == 2
        created.number == number
        created.state == 'DRAFT'
        created.workflow == [[state: 'DRAFT', enteredAt: start.toString()]]
        created.tasks*.number == tasks
        created.tasks*.details*.shortDescription == ['Task 1 of the CertScanner release',
                                                    'Task 2 of the CertScanner release']
        created.tasks*.state == ['OPEN', 'OPEN']
        created.tasks*.approval == ['NOT_APPROVED', 'NOT_APPROVED']
        created.tasks.every { !it.approvers.empty }
        created.approvals*.subMap('role', 'approver', 'state') == [
                [role: 'BUSINESS', approver: 'Rebecca Lawson', state: 'NOT_APPROVED'],
                [role: 'L1', approver: 'Olivia Bennett', state: 'NOT_APPROVED'],
                [role: 'L2', approver: 'James Carter', state: 'NOT_APPROVED'],
                [role: 'SUPPORT', approver: 'Jane Smith', state: 'NOT_APPROVED']]
        created.shortDescription == SUMMARY
        created.schedule == Json.parse(Json.jackson(draft.schedule()))

        when: 'two minutes later'
        moveTo(start + ofMinutes(2))
        def business = read(known)

        then: 'the business approver is asked'
        business.state == 'BUSINESS_APPROVAL'
        business.workflow*.state == ['DRAFT', 'BUSINESS_APPROVAL']
        business.workflow*.enteredAt == [start, start + ofMinutes(2)]*.toString()
        business.approvals*.state == ['REQUESTED', 'NOT_APPROVED', 'NOT_APPROVED', 'NOT_APPROVED']

        when: 'ten minutes after its creation'
        moveTo(start + ofMinutes(10))
        def approving = read(known)

        then: 'the approvers of the change approved it and the tasks wait for their groups'
        approving.state == 'CTASK_APPROVAL'
        approving.workflow*.state == ['DRAFT', 'BUSINESS_APPROVAL', 'PRIMARY_APPROVAL', 'SECONDARY_APPROVAL',
                                      'SUPPORT_APPROVAL', 'CTASK_APPROVAL']
        approving.workflow.last().enteredAt == (start + ofMinutes(10)).toString()
        approving.approvals*.state == ['APPROVED'] * 4
        approving.tasks*.approval == ['REQUESTED', 'REQUESTED']

        when: 'the groups approve the tasks two minutes apart'
        moveTo(start + ofMinutes(12))
        def oneApproved = read(known)
        moveTo(start + ofMinutes(14))
        def implementing = read(known)

        then: 'the change is in implementation, its tasks open until the installation'
        oneApproved.tasks*.approval == ['APPROVED', 'REQUESTED']
        oneApproved.state == 'CTASK_APPROVAL'
        implementing.state == 'IMPLEMENTATION'
        implementing.workflow.last() == [state: 'IMPLEMENTATION', enteredAt: (start + ofMinutes(14)).toString()]
        implementing.tasks*.approval == ['APPROVED', 'APPROVED']
        implementing.tasks*.state == ['OPEN', 'OPEN']

        and: 'every call to ProTech is in the call log of the user action, under the user decoded, and the change is followed'
        callLog()*.subMap('userName', 'correlationId', 'operation', 'target', 'outcome') == [
                [userName: USER, correlationId: correlationId, operation: 'create', target: "$number $SUMMARY" as String, outcome: 'SUCCESS'],
                [userName: USER, correlationId: correlationId, operation: 'createTask',
                 target  : "${tasks[0]} $number Task 1 of the CertScanner release" as String, outcome: 'SUCCESS'],
                [userName: USER, correlationId: correlationId, operation: 'createTask',
                 target  : "${tasks[1]} $number Task 2 of the CertScanner release" as String, outcome: 'SUCCESS']] +
                [[userName: USER, correlationId: correlationId, operation: 'read', target: number, outcome: 'SUCCESS']] * 5
        callLog()*.at == ([start] * 4 + [ofMinutes(2), ofMinutes(10), ofMinutes(12), ofMinutes(14)]
                .collect { start + it })*.toString()
        conditions.eventually {
            assert tracked(number).subMap('STATE', 'CORRELATION_ID') == [STATE: 'IMPLEMENTATION', CORRELATION_ID: correlationId]
        }
    }

    def "an update shows in ProTech once the demo applied it, with the tasks it names"() {
        given:
        Instant start = clock.instant()
        def draft = draft(start, start + ofDays(3))
        String number = create(draft)
        String first = createTask(number, 1)
        String second = createTask(number, 2)
        def known = draft.toBuilder().number(number).build()
        def current = read(known)
        def edited = current + [shortDescription: "$SUMMARY and renewals" as String,
                                tasks           : current.tasks.findAll { it.number == first }]

        when:
        def updated = api.put("/api/protech/changes/$number", edited)
        def before = read(known)

        then: 'ProTech takes the update and has not applied it yet'
        updated.status == 204
        updated.body == ''
        before.shortDescription == SUMMARY
        before.tasks*.state == ['OPEN', 'OPEN']

        when: 'the apply delay of the demo passed'
        moveTo(clock.instant() + ofSeconds(3))
        def after = read(known)

        then: 'the change has its new summary and the task the update left out is canceled'
        after.shortDescription == "$SUMMARY and renewals"
        after.tasks*.number == [first, second]
        after.tasks*.state == ['OPEN', 'CANCELED']
        callLog().find { it.operation == 'update' }.subMap('target', 'outcome', 'userName') ==
                [target: number, outcome: 'SUCCESS', userName: USER]
    }

    def "a reminder goes to the approver of the role or to the approvers of the task, and is logged"() {
        given:
        Instant start = clock.instant()
        def draft = draft(start, start + ofDays(3))
        String number = create(draft)
        String taskNumber = createTask(number, 1)
        moveTo(start + ofMinutes(2))
        def current = read(draft.toBuilder().number(number).build())

        when:
        def business = api.post("/api/protech/changes/$number/reminders", [role: 'BUSINESS', task: null])
        def ofTask = api.post("/api/protech/changes/$number/reminders", [role: null, task: taskNumber])

        then:
        business.status == 200
        business.json == ['Rebecca Lawson']
        ofTask.status == 200
        ofTask.json == current.tasks[0].approvers
        !ofTask.json.empty
        callLog().findAll { it.operation == 'remind' }*.subMap('target', 'outcome') == [
                [target: "$number BUSINESS" as String, outcome: 'SUCCESS'],
                [target: "$number $taskNumber" as String, outcome: 'SUCCESS']]
    }

    def "what ProTech refuses is a conflict naming the reason, and the refused call is logged"() {
        when:
        def created = api.postRaw('/api/protech/changes/CHG0000001/tasks', 'application/json',
                Json.jackson(task(1).withNumber(null)))
        def reminder = api.post('/api/protech/changes/CHG0000001/reminders', [role: 'L1'])

        then:
        [created, reminder].every { it.status == 409 }
        created.header('Content-Type').startsWith('application/problem+json')
        created.json.subMap('title', 'status', 'detail') ==
                [title: 'Conflict', status: 409, detail: 'ProTech has no change CHG0000001']
        callLog()*.subMap('operation', 'outcome', 'message') == [
                [operation: 'createTask', outcome: 'FAILURE', message: 'ProTech has no change CHG0000001'],
                [operation: 'remind', outcome: 'FAILURE', message: 'ProTech has no change CHG0000001']]
        tracked('CHG0000001') == null
    }
}

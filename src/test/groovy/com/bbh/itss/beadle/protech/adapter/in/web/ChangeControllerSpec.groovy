package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.application.change.ChangeService
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.RaisedChange
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep
import com.bbh.itss.beadle.protech.domain.change.TrackedChange
import com.bbh.itss.beadle.protech.domain.shared.RefusedException
import com.bbh.itss.beadle.protech.support.ControllerSpecification
import groovy.json.JsonOutput

import java.time.Clock

import static com.bbh.itss.beadle.protech.application.change.ChangeService.ONE_OF_ROLE_OR_TASK
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.RAISED
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.details
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static com.bbh.itss.beadle.protech.support.Json.jackson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class ChangeControllerSpec extends ControllerSpecification {

    static final String SCHEDULE_REQUIRED = 'choose when the change is installed, validated and first used'

    ProTechPort proTech = Mock()
    TrackedChangePort tracked = Mock()

    @Override
    Object controller() {
        new ChangeController(new ChangeService(proTech, tracked, Clock.systemUTC()))
    }

    def "a draft is raised in ProTech and answered 201 with its number and link"() {
        given:
        def draft = raised(number: null, tasks: [])

        when:
        def response = call(records(post('/api/protech/changes'), draft))

        then:
        1 * proTech.raise(draft) >> new RaisedChange('CHG0031001', 'https://bbh.service-now.com/CHG0031001')
        response.status == 201
        bodyOf(response) == [number: 'CHG0031001', url: 'https://bbh.service-now.com/CHG0031001']
    }

    def "a raised change is followed under the correlation id of the call of Beadle-GUI"() {
        when:
        def response = call(records(post('/api/protech/changes'), raised(number: null))
                .header('X-Beadle-User', 'Mateusz Matan').header('X-Correlation-Id', 'corr-7'))

        then:
        1 * proTech.raise(_) >> new RaisedChange('CHG0031001', null)
        1 * tracked.track({ List<TrackedChange> changes ->
            changes*.number() == ['CHG0031001'] && changes*.correlationId() == ['corr-7']
        })
        response.status == 201
    }

    def "the demo ProTech answers a raised change without a link"() {
        given:
        proTech.raise(_) >> new RaisedChange('CHG0031001', null)

        expect:
        bodyOf(call(records(post('/api/protech/changes'), raised(number: null)))) == [number: 'CHG0031001', url: null]
    }

    def "a draft ProTech would refuse is refused with the fields at fault"() {
        when:
        def response = call(records(post('/api/protech/changes'), raised(shortDescription: ' ', schedule: null)))

        then:
        0 * proTech.raise(_)
        response.status == 400
        response.contentType == 'application/problem+json'
        def problem = bodyOf(response)
        problem.title == 'Validation failed'
        problem.status == 400
        problem.problems == [[field: 'shortDescription', message: 'is required'],
                             [field: 'schedule', message: SCHEDULE_REQUIRED]]
    }

    def "a body that is #what is a malformed request"() {
        when:
        def response = call(post('/api/protech/changes').contentType(APPLICATION_JSON).content(body))

        then:
        0 * proTech.raise(_)
        response.status == 400
        bodyOf(response).detail == detail
        bodyOf(response).problems == problems

        where:
        what            | body                  || detail                                    | problems
        'no JSON'       | '{"shortDescription"' || 'The request body is not valid JSON.'     | null
        'missing'       | ''                    || 'The request body is missing.'            | null
        'an odd state'  | '{"state":"FLYING"}'  || 'state must be one of DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, ESCALATED_APPROVAL, IMPLEMENTATION, CLOSED' | [[field: 'state', message: 'must be one of DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, SUPPORT_APPROVAL, CTASK_APPROVAL, ESCALATED_APPROVAL, IMPLEMENTATION, CLOSED']]
    }

    def "a change task is created on its change and answered 201 with its number"() {
        given:
        def created = new ChangeTask(null, details(1), null, null, null, null, null)

        when:
        def response = call(records(post('/api/protech/changes/CHG0031001/tasks'), created))

        then:
        1 * proTech.createTask('CHG0031001', created) >> 'CTASK0041001'
        response.status == 201
        bodyOf(response) == [number: 'CTASK0041001']
    }

    def "a change task without its group and texts is refused with the fields at fault"() {
        when:
        def response = call(json(post('/api/protech/changes/CHG0031001/tasks'), [details: [assignedTo: 'Ann Lee']]))

        then:
        0 * proTech.createTask(*_)
        response.status == 400
        bodyOf(response).problems*.field == ['details.assignmentGroup', 'details.shortDescription',
                                             'details.description']
    }

    def "the changes Beadle knows are read back as ProTech holds them, by number"() {
        given:
        def known = [raised(), raised(number: 'CHG0031002')]
        def moved = raised(state: IMPLEMENTATION, workflow: [new WorkflowStep(IMPLEMENTATION, RAISED)])

        when:
        def response = call(records(post('/api/protech/changes/read'), known))

        then:
        1 * proTech.read(known) >> [CHG0031001: moved]
        response.status == 200
        def answer = bodyOf(response)
        answer.keySet() == ['CHG0031001'] as Set
        answer.CHG0031001.state == 'IMPLEMENTATION'
        answer.CHG0031001.workflow == [[state: 'IMPLEMENTATION', enteredAt: '2026-10-05T09:00:00Z']]
        jackson(JsonOutput.toJson(answer.CHG0031001), ProductionChange) == moved
    }

    def "reading nothing asks ProTech nothing, and reading a change without a number is refused"() {
        expect:
        bodyOf(call(records(post('/api/protech/changes/read'), []))) == [:]

        when:
        def response = call(records(post('/api/protech/changes/read'), [raised(), raised(number: ' ')]))

        then:
        0 * proTech.read(_)
        response.status == 400
        bodyOf(response).problems == [[field: '[1].number', message: 'is required']]
    }

    def "an edited change is sent to ProTech and answered 204"() {
        when:
        def response = call(records(put('/api/protech/changes/CHG0031001'), raised()))

        then:
        1 * proTech.update(raised())
        response.status == 204
        response.contentAsString == ''
    }

    def "an edit of another change than the path names is refused"() {
        when:
        def response = call(records(put('/api/protech/changes/CHG0031002'), raised()))

        then:
        0 * proTech.update(_)
        response.status == 400
        bodyOf(response).problems == [[field: 'number', message: 'must be CHG0031002, the change of the path']]
    }

    def "an edit ProTech refuses is a conflict, and ProTech out of reach a bad gateway"() {
        given:
        proTech.update(_) >> { throw failure }

        when:
        def response = call(records(put('/api/protech/changes/CHG0031001'), raised()))

        then:
        response.status == status
        bodyOf(response).detail == detail

        where:
        failure                                                                           || status | detail
        new RefusedException('ProTech does not change a closed change')                  || 409    | failure.message
        new UncheckedIOException('ProTech could not update CHG0031001: 503 Service Unavailable',
                new IOException())                                                        || 502    | failure.message
        new IllegalStateException('Cannot cast the change to ProductionChange')           || 500    | 'Beadle-ProTech could not handle the request. The failure is in its log.'
    }

    def "a reminder goes to the approver of a role or the approvers of a task and answers whom it reached"() {
        when:
        def response = call(json(post('/api/protech/changes/CHG0031001/reminders'), body))

        then:
        1 * proTech.remind('CHG0031001', approval) >> reached
        response.status == 200
        bodyOf(response) == reached

        where:
        body                                  || approval                           | reached
        [role: 'BUSINESS', task: null]        || ApprovalRef.of(BUSINESS)           | ['Rebecca Lawson']
        [role: null, task: ' CTASK0041001 ']  || ApprovalRef.ofTask('CTASK0041001') | ['Rebecca Lawson', 'Thomas Ashby']
    }

    def "a reminder names either a role or a task: #body"() {
        when:
        def response = call(json(post('/api/protech/changes/CHG0031001/reminders'), body))

        then:
        0 * proTech.remind(*_)
        response.status == 400
        bodyOf(response).problems == problems

        where:
        body                                   || problems
        [role: null, task: null]               || [[field: 'task', message: ONE_OF_ROLE_OR_TASK]]
        [role: 'BUSINESS', task: 'CTASK00001'] || [[field: 'task', message: ONE_OF_ROLE_OR_TASK]]
        [role: 'BOSS']                         || [[field: 'role', message: 'must be one of BUSINESS, L1, L2, SUPPORT']]
    }

    def "a reminder nobody can receive is a conflict"() {
        given:
        proTech.remind(*_) >> { throw new RefusedException('ProTech has nobody to remind on CHG0031001') }

        when:
        def response = call(json(post('/api/protech/changes/CHG0031001/reminders'), [role: 'L1']))

        then:
        response.status == 409
        bodyOf(response).detail == 'ProTech has nobody to remind on CHG0031001'
    }

    def "#endpoint needs the service token"() {
        when:
        def responses = unauthorized().collect { callWithout(request, it) }

        then:
        responses*.status == [401] * unauthorized().size()
        0 * proTech._

        where:
        endpoint << ['POST /api/protech/changes', 'POST /api/protech/changes/{number}/tasks',
                     'POST /api/protech/changes/read', 'PUT /api/protech/changes/{number}',
                     'POST /api/protech/changes/{number}/reminders']
        request << [records(post('/api/protech/changes'), raised(number: null)),
                    records(post('/api/protech/changes/CHG0031001/tasks'), task(1)),
                    records(post('/api/protech/changes/read'), [raised()]),
                    records(put('/api/protech/changes/CHG0031001'), raised()),
                    json(post('/api/protech/changes/CHG0031001/reminders'), [role: 'L1'])]
    }
}

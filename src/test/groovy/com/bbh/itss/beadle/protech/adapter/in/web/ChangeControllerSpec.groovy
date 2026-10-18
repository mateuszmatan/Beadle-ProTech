package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.application.change.ChangeService
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.CreatedChange
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.TrackedChange
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep
import com.bbh.itss.beadle.protech.support.ControllerSpecification
import groovy.json.JsonOutput

import java.time.Clock

import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.CREATED_AT
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.details
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.issued
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static com.bbh.itss.beadle.protech.support.Json.jackson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class ChangeControllerSpec extends ControllerSpecification {

    ProTechPort proTech = Mock()
    TrackedChangePort tracked = Mock()

    @Override
    Object controller() {
        new ChangeController(new ChangeService(proTech, tracked, Clock.systemUTC()))
    }

    def "a draft is created in ProTech and answered 201 with its number and link"() {
        given:
        def draft = issued(number: null, tasks: [])

        when:
        def response = call(records(post('/api/protech/changes'), draft))

        then:
        1 * proTech.create(draft) >> new CreatedChange('CHG0031001', 'https://protech.bbh.test/CHG0031001')
        response.status == 201
        bodyOf(response) == [number: 'CHG0031001', url: 'https://protech.bbh.test/CHG0031001']
    }

    def "a created change is followed under the correlation id of the call of Beadle-GUI"() {
        when:
        def response = call(records(post('/api/protech/changes'), issued(number: null))
                .header('X-Beadle-User', 'Mateusz Matan').header('X-Correlation-Id', 'corr-7'))

        then:
        1 * proTech.create(_) >> new CreatedChange('CHG0031001', null)
        1 * tracked.track({ List<TrackedChange> changes ->
            changes*.number() == ['CHG0031001'] && changes*.correlationId() == ['corr-7']
        })
        response.status == 201
    }

    def "a body that is #what is a malformed request"() {
        when:
        def response = call(post('/api/protech/changes').contentType(APPLICATION_JSON).content(body))

        then:
        0 * proTech.create(_)
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

    def "the changes Beadle knows are read back as ProTech holds them, by number"() {
        given:
        def known = [issued(), issued(number: 'CHG0031002')]
        def moved = issued(state: IMPLEMENTATION, workflow: [new WorkflowStep(IMPLEMENTATION, CREATED_AT)])

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

    def "an edited change is sent to ProTech and answered 204"() {
        when:
        def response = call(records(put('/api/protech/changes/CHG0031001'), issued()))

        then:
        1 * proTech.update(issued())
        response.status == 204
        response.contentAsString == ''
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
        [role: 'BUSINESS', task: null]        || new ApprovalRef(BUSINESS, null)    | ['Rebecca Lawson']
        [role: null, task: ' CTASK0041001 ']  || new ApprovalRef(null, 'CTASK0041001') | ['Rebecca Lawson', 'Thomas Ashby']
    }
}

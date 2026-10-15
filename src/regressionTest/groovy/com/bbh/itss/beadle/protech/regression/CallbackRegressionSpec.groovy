package com.bbh.itss.beadle.protech.regression

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification

import java.time.Instant

import static java.time.Duration.ofDays
import static java.time.Duration.ofHours
import static java.time.Duration.ofMinutes

class CallbackRegressionSpec extends BeadleProTechSpecification {

    def "Beadle-GUI hears of every state, approval and task move of a change it raised, with the service token and the correlation id of the raise"() {
        given: 'a change installed in an hour, with two tasks'
        Instant start = clock.instant()
        String number = raise(draft(start, start + ofHours(1)))
        createTask(number, 1)
        createTask(number, 2)

        when: 'the business approver is asked'
        moveTo(start + ofMinutes(2))

        then:
        conditions.eventually {
            assert events(number) == [['STATE_CHANGED', 'BUSINESS_APPROVAL', start + ofMinutes(2)]]
        }
        with(gui.callbacksFor(number).first()) {
            method == 'POST'
            url == 'http://beadle-gui.test:8081/api/callbacks/protech'
            header('Authorization') == "Bearer $TOKEN".toString()
            header('X-Beadle-User') == 'system'
            header('X-Correlation-Id') == correlationId
            header('Content-Type').startsWith('application/json')
            body == [number: number, event: 'STATE_CHANGED', state: 'BUSINESS_APPROVAL',
                     at: (start + ofMinutes(2)).toString(), correlationId: correlationId]
            status == 202
        }
        conditions.eventually {
            assert tracked(number).STATE == 'BUSINESS_APPROVAL'
        }

        when: 'the approvers of the change approved it and the tasks wait for their groups'
        moveTo(start + ofMinutes(10))

        then: 'one callback covers the stages passed in between'
        conditions.eventually {
            assert events(number).last() == ['STATE_CHANGED', 'CTASK_APPROVAL', start + ofMinutes(10)]
        }
        events(number).size() == 2

        when: 'the group of the first task approves it'
        moveTo(start + ofMinutes(12))

        then:
        conditions.eventually {
            assert events(number).last() == ['APPROVAL_CHANGED', 'CTASK_APPROVAL', start + ofMinutes(12)]
        }

        when: 'the second task is approved and the change, on short notice, goes past the escalation to implementation'
        moveTo(start + ofMinutes(16))

        then:
        conditions.eventually {
            assert events(number).last() == ['STATE_CHANGED', 'IMPLEMENTATION', start + ofMinutes(16)]
        }

        when: 'the installation starts'
        moveTo(start + ofHours(1))

        then: 'the tasks go in progress'
        conditions.eventually {
            assert events(number).last() == ['TASK_CHANGED', 'IMPLEMENTATION', start + ofHours(1)]
        }

        when: 'the validation ends'
        moveTo(start + ofHours(4))

        then: 'the change is closed and no longer followed'
        conditions.eventually {
            assert events(number).last() == ['STATE_CHANGED', 'CLOSED', start + ofHours(4)]
        }
        conditions.eventually {
            assert tracked(number).STATE == 'CLOSED'
        }
        events(number)*.take(2) == [['STATE_CHANGED', 'BUSINESS_APPROVAL'], ['STATE_CHANGED', 'CTASK_APPROVAL'],
                                    ['APPROVAL_CHANGED', 'CTASK_APPROVAL'], ['STATE_CHANGED', 'IMPLEMENTATION'],
                                    ['TASK_CHANGED', 'IMPLEMENTATION'], ['STATE_CHANGED', 'CLOSED']]
        gui.callbacksFor(number).every { it.status == 202 && it.header('X-Correlation-Id') == correlationId }

        and: 'the callbacks are in the call log of the raise, as calls of the system to Beadle-GUI'
        callLog('GUI')*.subMap('userName', 'correlationId', 'operation', 'target', 'outcome') ==
                events(number).collect { event, state, at ->
                    [userName: 'system', correlationId: correlationId, operation: 'notify',
                     target  : "$number $event" as String, outcome: 'SUCCESS']
                }
    }

    def "a change Beadle-GUI only read is followed as well, under the correlation id of the read"() {
        given: 'a change ProTech has from before, which Beadle-GUI reads'
        Instant start = clock.instant()
        String number = 'CHG00' + (10000 + new Random().nextInt(90000))
        def known = draft(start, start + ofDays(3)).toBuilder().number(number).build()
        read(known)

        expect:
        tracked(number).subMap('STATE', 'CORRELATION_ID') == [STATE: 'DRAFT', CORRELATION_ID: correlationId]

        when:
        moveTo(start + ofMinutes(2))

        then:
        conditions.eventually {
            assert events(number) == [['STATE_CHANGED', 'BUSINESS_APPROVAL', start + ofMinutes(2)]]
        }
        gui.callbacksFor(number).first().header('X-Correlation-Id') == correlationId
    }

    def "a callback Beadle-GUI did not take is sent again on the next look until it takes it"() {
        given:
        Instant start = clock.instant()
        String number = raise(draft(start, start + ofDays(3)))
        gui.refuse(number, 2)

        when:
        moveTo(start + ofMinutes(2))

        then:
        conditions.eventually {
            assert gui.callbacksFor(number)*.status == [503, 503, 202]
        }
        events(number) == [['STATE_CHANGED', 'BUSINESS_APPROVAL', start + ofMinutes(2)]] * 3
        conditions.eventually {
            assert tracked(number).STATE == 'BUSINESS_APPROVAL'
        }
        callLog('GUI')*.outcome == ['FAILURE', 'FAILURE', 'SUCCESS']
        callLog('GUI').findAll { it.outcome == 'FAILURE' }
                .every { it.message.startsWith("Beadle-GUI could not take the callback for $number: 503") }
    }

    /** The event, the state and the time of every callback Beadle-GUI got for {@code number}, oldest first. */
    private List<List> events(String number) {
        gui.callbacksFor(number).collect { [it.body.event, it.body.state, Instant.parse(it.body.at as String)] }
    }
}

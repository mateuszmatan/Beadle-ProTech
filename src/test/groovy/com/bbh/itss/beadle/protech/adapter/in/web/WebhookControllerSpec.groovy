package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.protech.application.change.ChangeWatchService
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback
import com.bbh.itss.beadle.protech.support.ControllerSpecification

import java.time.Clock
import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.CALLED
import static com.bbh.itss.beadle.protech.support.CallLogFixtures.trackedChange
import static java.time.ZoneOffset.UTC
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class WebhookControllerSpec extends ControllerSpecification {

    static final Instant NOW = CALLED.plusSeconds(600)

    TrackedChangePort tracked = Mock()
    ProTechPort proTech = Mock()
    CallbackPort callbacks = Mock()

    @Override
    Object controller() {
        new WebhookController(new ChangeWatchService(tracked, proTech, callbacks, Clock.fixed(NOW, UTC)))
    }

    def "ProTech's webhook has a followed change looked at at once, and Beadle-GUI is told it moved"() {
        given:
        def change = trackedChange()
        def activity = new ChangeActivity('CHG0031001', BUSINESS_APPROVAL, NOW.minusSeconds(5), null, null)

        when:
        def response = call(json(post('/api/protech/webhook'), [number: ' CHG0031001 ']))

        then:
        1 * tracked.find('CHG0031001') >> Optional.of(change)
        1 * proTech.activity([change], CALLED.minusSeconds(60)) >> [CHG0031001: activity]
        1 * callbacks.notify(new ChangeCallback('CHG0031001', STATE_CHANGED, BUSINESS_APPROVAL, NOW.minusSeconds(5)))
        1 * tracked.reported(change, activity, NOW)
        response.status == 202
        response.contentAsString == ''
    }

    def "a change that is not followed, or no longer, is left alone: #number"() {
        when:
        def response = call(json(post('/api/protech/webhook'), [number: number]))

        then:
        1 * tracked.find(number) >> found
        0 * proTech._
        0 * callbacks._
        response.status == 202

        where:
        number       || found
        'CHG0039999' || Optional.empty()
        'CHG0031002' || Optional.of(trackedChange(number: 'CHG0031002', state: CLOSED))
    }

    def "a webhook call without a number is refused naming the field: #body"() {
        when:
        def response = call(json(post('/api/protech/webhook'), body))

        then:
        0 * tracked._
        response.status == 400
        bodyOf(response).problems == [[field: 'number', message: 'is required']]

        where:
        body << [[:], [number: ' '], [number: null]]
    }

    def "the webhook needs the service token: #authorization"() {
        expect:
        callWithout(json(post('/api/protech/webhook'), [number: 'CHG0031001']), authorization).status == 401

        where:
        authorization << unauthorized()
    }
}

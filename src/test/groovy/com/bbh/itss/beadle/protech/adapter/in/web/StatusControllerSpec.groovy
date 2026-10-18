package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.application.change.ChangeService
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.support.ControllerSpecification

import java.time.Clock

import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.DEMO
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.LIVE
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class StatusControllerSpec extends ControllerSpecification {

    ProTechPort proTech = Stub()
    TrackedChangePort tracked = Stub()

    @Override
    Object controller() {
        new StatusController(new ChangeService(proTech, tracked, Clock.systemUTC()))
    }

    def "the status says whether ProTech is reached and whether it is the demo or the live one"() {
        given:
        proTech.mode() >> mode

        when:
        def response = call(get('/api/protech/status'))

        then:
        response.status == 200
        response.contentType == 'application/json'
        bodyOf(response) == [connected: connected, mode: label]

        where:
        connected | mode || label
        false     | DEMO || 'demo'
        true      | LIVE || 'live'
    }
}

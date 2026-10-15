package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.application.change.LookupService
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.protech.domain.change.Lookup
import com.bbh.itss.beadle.protech.support.ControllerSpecification

import static com.bbh.itss.beadle.protech.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.USERS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class LookupControllerSpec extends ControllerSpecification {

    ProTechLookupPort lookups = Mock()

    @Override
    Object controller() {
        new LookupController(new LookupService(lookups))
    }

    def "a lookup answers the records of its kind that match the text, at most the limit"() {
        when:
        def response = call(get('/api/protech/lookups/users').param('q', ' smith ').param('limit', '5'))

        then:
        1 * lookups.find(USERS, 'smith', 5) >> [new Lookup('Jane Smith', 'jane.smith@bbh.com')]
        response.status == 200
        bodyOf(response) == [[value: 'Jane Smith', detail: 'jane.smith@bbh.com']]
    }

    def "without a text or a limit a lookup lists the first 20"() {
        when:
        def response = call(get('/api/protech/lookups/assignment-groups'))

        then:
        1 * lookups.find(ASSIGNMENT_GROUPS, '', 20) >> []
        response.status == 200
        bodyOf(response) == []
    }

    def "a limit of #limit is refused with the field at fault"() {
        when:
        def response = call(get('/api/protech/lookups/users').param('limit', limit))

        then:
        0 * lookups.find(*_)
        response.status == 400
        response.contentType == 'application/problem+json'
        bodyOf(response).problems == [[field: 'limit', message: 'must be between 1 and 100']]

        where:
        limit << ['0', '101', '-1']
    }

    def "a limit that is no number is a malformed request"() {
        expect:
        call(get('/api/protech/lookups/users').param('limit', 'many')).status == 400
    }

    def "an unknown kind of lookup is not found"() {
        when:
        def response = call(get('/api/protech/lookups/cars'))

        then:
        response.status == 404
        bodyOf(response).detail == 'Lookup cars does not exist'
    }

    def "ProTech out of reach is a bad gateway"() {
        given:
        lookups.find(*_) >> { throw new UncheckedIOException('ProTech could not look up the users: Read timed out',
                new IOException()) }

        when:
        def response = call(get('/api/protech/lookups/users'))

        then:
        response.status == 502
        bodyOf(response).detail == 'ProTech could not look up the users: Read timed out'
    }

    def "a lookup needs the service token: #authorization"() {
        expect:
        callWithout(get('/api/protech/lookups/users'), authorization).status == 401

        where:
        authorization << unauthorized()
    }
}

package com.bbh.itss.beadle.protech.application.change

import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.protech.domain.change.Lookup
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.protech.domain.shared.NotFoundException
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CONFIGURATION_ITEMS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.USERS

class LookupServiceSpec extends Specification {

    ProTechLookupPort lookups = Mock()
    def service = new LookupService(lookups)

    def "a lookup of #kind asks ProTech for its #expected with the trimmed query and the limit, 20 by default"() {
        when:
        def found = service.find(kind, query, limit)

        then:
        1 * lookups.find(expected, trimmed, size) >> [new Lookup('Ann Lee', 'ann.lee@bbh.com')]
        found == [new Lookup('Ann Lee', 'ann.lee@bbh.com')]

        where:
        kind                  | query   | limit || expected            | trimmed | size
        'users'               | ' ann ' | null  || USERS               | 'ann'   | 20
        'assignment-groups'   | null    | 5     || ASSIGNMENT_GROUPS   | ''      | 5
        'configuration-items' | 'cert'  | 100   || CONFIGURATION_ITEMS | 'cert'  | 100
    }

    def "a kind ProTech does not look up does not exist"() {
        when:
        service.find('people', '', null)

        then:
        0 * lookups._
        def missing = thrown(NotFoundException)
        missing.message == 'Lookup people does not exist'
    }

    def "a limit of #limit is refused"() {
        when:
        service.find('users', '', limit)

        then:
        0 * lookups._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['limit']
        refused.message == 'must be between 1 and 100'

        where:
        limit << [0, -1, 101]
    }
}

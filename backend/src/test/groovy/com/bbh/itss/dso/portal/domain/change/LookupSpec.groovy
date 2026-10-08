package com.bbh.itss.dso.portal.domain.change

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.CONFIGURATION_ITEMS

class LookupSpec extends Specification {

    def "a lookup kind is found by the path of its URL"() {
        expect:
        LookupKind.values()*.path() == ['users', 'departments', 'assignment-groups', 'releases', 'configuration-items',
                                        'incidents', 'problems', 'clients']
        LookupKind.values().every { LookupKind.of(it.path()) == it }
        LookupKind.of('assignment-groups') == ASSIGNMENT_GROUPS
        LookupKind.of('configuration-items') == CONFIGURATION_ITEMS
    }

    def "an unknown lookup kind #path does not exist"() {
        when:
        LookupKind.of(path)

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == "Lookup $path does not exist"

        where:
        path << ['owners', 'USERS', 'assignment_groups', null]
    }

    def "a lookup matches #query when its value or detail contains it, ignoring case"() {
        expect:
        new Lookup('Grace Turner', 'grace.turner@bbh.com').matches(query) == person
        new Lookup('INC0104211', null).matches(query) == incident

        where:
        query    || person | incident
        null     || true   | true
        ''       || true   | true
        'grace'  || true   | false
        'TURNER' || true   | false
        '@BBH'   || true   | false
        'inc01'  || false  | true
        'Olivia' || false  | false
    }
}

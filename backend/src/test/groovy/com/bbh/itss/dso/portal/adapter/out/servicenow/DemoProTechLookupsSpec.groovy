package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort
import com.bbh.itss.dso.portal.domain.change.ChangeProduct
import com.bbh.itss.dso.portal.domain.change.Lookup
import com.bbh.itss.dso.portal.domain.change.LookupKind
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.CLIENTS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.CONFIGURATION_ITEMS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.DEPARTMENTS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.INCIDENTS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.PROBLEMS
import static com.bbh.itss.dso.portal.domain.change.LookupKind.RELEASES
import static com.bbh.itss.dso.portal.domain.change.LookupKind.USERS

class DemoProTechLookupsSpec extends Specification {

    ChangeProductsPort products = Stub() {
        findAll() >> [summary('NAVCALC', 'NAV Calculator', 'Fund Accounting', 'Fund Services'),
                       summary('PAYHUB', 'Payments Hub', 'Payments Engineering', 'Fund Services'),
                       summary('LEDGER', 'Ledger', ' ', 'Custody')]
    }
    def lookups = new DemoProTechLookups(products)

    def "the users are BBH people with their e-mail, among them the signed-in user and the demo approvers"() {
        when:
        def users = lookups.find(USERS, '', 100)

        then:
        users*.value().containsAll(['Mateusz Matan', 'Olivia Bennett', 'James Carter', 'Grace Turner',
                                    'Rebecca Lawson', 'Priya Natarajan', 'Marcus Webb'])
        users.find { it.value() == 'Mateusz Matan' }.detail() == 'mateusz.matan@bbh.com'
        users*.value() == users*.value().unique(false)
    }

    def "the departments hold the five departments of the portal"() {
        expect:
        lookups.find(DEPARTMENTS, null, 100)*.value().containsAll(['AI Lab', 'Capital Partners',
                                                                   'Corporate Technology', 'Custody', 'Fund Services'])
        lookups.find(DEPARTMENTS, null, 100).every { it.detail() ==~ /Cost centre \d{4}/ }
    }

    def "the configuration items, releases and assignment groups come from the product catalogue"() {
        when:
        def releases = lookups.find(RELEASES, null, 100)

        then:
        lookups.find(CONFIGURATION_ITEMS, null, 100) == [new Lookup('NAV Calculator', 'Fund Accounting'),
                                                         new Lookup('Payments Hub', 'Payments Engineering'),
                                                         new Lookup('Ledger', 'Custody')]
        releases.size() == 9
        releases.findAll { it.detail() == 'PAYHUB' }*.value().every { it ==~ /Payments Hub \d\.\d/ }
        releases == lookups.find(RELEASES, null, 100)
        lookups.find(ASSIGNMENT_GROUPS, 'support', 100).take(3) == [
                new Lookup('Fund Accounting Application Support', 'Fund Services'),
                new Lookup('Payments Engineering Application Support', 'Fund Services'),
                new Lookup('Ledger Application Support', 'Custody')]
        lookups.find(ASSIGNMENT_GROUPS, 'Database', 100) ==
                [new Lookup('Database Administration', 'Infrastructure & Operations')]
    }

    def "incidents, problems and clients are ProTech-like records with a short description"() {
        expect:
        lookups.find(INCIDENTS, null, 100).every { it.value() ==~ /INC0\d{6}/ && it.detail() }
        lookups.find(PROBLEMS, null, 100).every { it.value() ==~ /PRB0\d{6}/ && it.detail() }
        !lookups.find(CLIENTS, null, 100).isEmpty()
    }

    def "a lookup finds #query in the value or the detail, ignoring case, and returns at most the limit"() {
        expect:
        lookups.find(kind, query, limit)*.value() == expected

        where:
        kind      | query            | limit || expected
        USERS     | 'GRACE'          | 20    || ['Grace Mitchell', 'Grace Turner']
        USERS     | 'mateusz.matan@' | 20    || ['Mateusz Matan']
        INCIDENTS | 'nav report'     | 20    || ['INC0104377']
        PROBLEMS  | 'tls'            | 20    || ['PRB0040319']
        CLIENTS   | 'private equity' | 2     || ['Cedar Point Capital', 'Granite Peak Partners']
        USERS     | 'nobody'         | 20    || []
    }

    def "every kind answers an empty query with its first entries"() {
        expect:
        LookupKind.values().every { kind ->
            def first = lookups.find(kind, '', 2)
            assert first.size() == 2
            assert first == lookups.find(kind, null, 100).take(2)
            true
        }
    }

    static ChangeProduct summary(String code, String name, String ownerTeam, String department) {
        new ChangeProduct(1L, code, name, null, ownerTeam, 3L, department, null)
    }
}

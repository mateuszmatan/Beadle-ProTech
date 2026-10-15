package com.bbh.itss.beadle.protech.adapter.out.protech

import com.bbh.itss.beadle.protech.domain.change.Lookup
import com.bbh.itss.beadle.protech.domain.change.LookupKind
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CLIENTS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CONFIGURATION_ITEMS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.DEPARTMENTS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.INCIDENTS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.PROBLEMS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.RELEASES
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.USERS
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.DEMO
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.RELEASE_MANAGEMENT

class DemoProTechLookupsSpec extends Specification {

    def lookups = new DemoProTechLookups()

    def "the demo lookups say they are the demo and not connected to ProTech"() {
        expect:
        !lookups.connected()
        lookups.mode() == DEMO
    }

    def "the users are BBH people with their e-mail, among them the demo approvers, each once"() {
        when:
        def users = lookups.find(USERS, '', 100)

        then:
        users*.value().containsAll(['Mateusz Matan', 'Olivia Bennett', 'James Carter', 'Grace Turner',
                                    'Rebecca Lawson', 'Priya Natarajan', 'Marcus Webb'])
        users.find { it.value() == 'Mateusz Matan' }.detail() == 'mateusz.matan@bbh.com'
        users*.value() == users*.value().unique(false)
        users.size() == DemoProTechLookups.USERS.size()
    }

    def "the departments are BBH's with their cost centre"() {
        when:
        def found = lookups.find(DEPARTMENTS, null, 100)

        then:
        found*.value() == ['AI Lab', 'Capital Partners', 'Compliance', 'Corporate Technology', 'Custody',
                           'Fund Services', 'Information Security', 'Infrastructure & Operations',
                           'Investor Services', 'Private Banking', 'Treasury']
        found.every { it.detail() ==~ /Cost centre \d{4}/ }
        lookups.find(DEPARTMENTS, 'operations', 20)*.value() == ['Infrastructure & Operations']
    }

    def "the assignment groups are the support groups of the product teams and the infrastructure groups"() {
        when:
        def groups = lookups.find(ASSIGNMENT_GROUPS, null, 100)

        then:
        groups.take(2) == [new Lookup('AI Lab Engineering Application Support', 'AI Lab'),
                           new Lookup('Private Equity Technology Application Support', 'Capital Partners')]
        groups.contains(new Lookup('Technology Architecture Application Support', 'Corporate Technology'))
        groups.contains(new Lookup(RELEASE_MANAGEMENT, 'Corporate Technology'))
        groups.contains(new Lookup('Database Administration', 'Infrastructure & Operations'))
        groups*.value() == groups*.value().unique(false)
        lookups.find(ASSIGNMENT_GROUPS, 'fund acc', 20)*.value() == ['Fund Accounting Application Support']
    }

    def "the releases are three consecutive versions of every product, the configuration items the products"() {
        when:
        def releases = lookups.find(RELEASES, 'certscanner', 20)
        def items = lookups.find(CONFIGURATION_ITEMS, null, 100)

        then:
        releases.size() == 3
        releases.every { it.value() ==~ /CertScanner \d\.\d/ && it.detail() == 'CERTSCANNER' }
        releases*.value() == releases*.value().sort(false)
        lookups.find(RELEASES, null, 100).size() == 3 * DemoProTechLookups.CATALOGUE.size()
        items*.value() == DemoProTechLookups.CATALOGUE*.name()
        items.find { it.value() == 'CertScanner' }.detail() == 'Technology Architecture'
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
}

package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUser
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase
import com.bbh.itss.dso.portal.domain.catalog.Department
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
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes

class DemoProTechLookupsSpec extends Specification {

    ChangeProductsPort products = Stub() {
        findAll() >> [summary('NAVCALC', 'NAV Calculator', 'Fund Accounting', 'Fund Services'),
                       summary('PAYHUB', 'Payments Hub', 'Payments Engineering', 'Fund Services'),
                       summary('LEDGER', 'Ledger', ' ', 'Custody')]
    }
    DepartmentRepositoryPort departments = Stub() {
        findAll() >> [new Department(1L, 'AI Lab', 0), new Department(3L, 'corporate technology', 1),
                      new Department(9L, 'Treasury Operations', 0)]
    }
    String signedIn = 'Mateusz Matan'
    SignedInUserUseCase users = Stub() {
        signedInUser() >> { new SignedInUser(signedIn) }
    }
    def lookups = new DemoProTechLookups(products, departments, users)

    def "the users are BBH people with their e-mail, among them the signed-in user and the demo approvers"() {
        when:
        def users = lookups.find(USERS, '', 100)

        then:
        users*.value().containsAll(['Mateusz Matan', 'Olivia Bennett', 'James Carter', 'Grace Turner',
                                    'Rebecca Lawson', 'Priya Natarajan', 'Marcus Webb'])
        users.find { it.value() == 'Mateusz Matan' }.detail() == 'mateusz.matan@bbh.com'
        users*.value() == users*.value().unique(false)
    }

    def "a signed-in user who is not one of ProTech's demo people is found too"() {
        given:
        signedIn = 'Zoë Quinn'

        expect:
        lookups.find(USERS, 'quinn', 20) == [new Lookup('Zoë Quinn', 'zo.quinn@bbh.com')]
        lookups.find(USERS, '', 100).size() == DemoProTechLookups.USERS.size() + 1
    }

    def "the departments are the portal's, also as added or renamed in Beadle Admin, and ProTech's others, each once"() {
        when:
        def found = lookups.find(DEPARTMENTS, null, 100)

        then:
        found*.value() == ['AI Lab', 'Capital Partners', 'Compliance', 'corporate technology', 'Custody',
                           'Fund Services', 'Information Security', 'Infrastructure & Operations',
                           'Investor Services', 'Private Banking', 'Treasury', 'Treasury Operations']
        found.find { it.value() == 'corporate technology' }.detail() == 'Cost centre 4310'
        found.every { it.detail() ==~ /Cost centre \d{4}/ }
        lookups.find(DEPARTMENTS, 'operations', 20)*.value() == ['Infrastructure & Operations', 'Treasury Operations']
    }

    def "the values stay within the ProTech fields they fill, however long the names in the portal are"() {
        given:
        def wide = new DemoProTechLookups(Stub(ChangeProductsPort) {
            findAll() >> [new ChangeProduct(1L, 'LONG', 'Ł' * 200, 'Ż' * 200, 3L, 'Custody', null)]
        }, Stub(DepartmentRepositoryPort) {
            findAll() >> [new Department(9L, 'Ś' * 100, 0)]
        }, Stub(SignedInUserUseCase) {
            signedInUser() >> new SignedInUser('Ü' * 150)
        })

        when:
        def releases = wide.find(RELEASES, null, 20)*.value()
        def items = wide.find(CONFIGURATION_ITEMS, null, 20)*.value()
        def groups = wide.find(ASSIGNMENT_GROUPS, 'Ż', 20)*.value()
        def named = wide.find(DEPARTMENTS, 'Ś', 20)*.value()
        def people = wide.find(USERS, 'Ü', 20)*.value()

        then:
        releases.size() == 3
        releases.every { bytes(it) <= 100 && it ==~ /Ł+\.\.\. \d\.\d/ }
        items.size() == 1 && bytes(items[0]) <= 200 && items[0] ==~ /Ł+\.\.\./
        groups.size() == 1 && bytes(groups[0]) <= 200 && groups[0] ==~ /Ż+\.\.\. Application Support/
        named.size() == 1 && bytes(named[0]) <= 100 && named[0] ==~ /Ś+\.\.\./
        people.size() == 1 && bytes(people[0]) <= 200 && people[0] ==~ /Ü+\.\.\./
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
        new ChangeProduct(1L, code, name, ownerTeam, 3L, department, null)
    }
}

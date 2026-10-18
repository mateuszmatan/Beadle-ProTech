package com.bbh.itss.beadle.protech.regression

import com.bbh.itss.beadle.protech.support.BeadleProTechSpecification

class LookupRegressionSpec extends BeadleProTechSpecification {

    def "the #kind of ProTech are found by a part of their name or detail"() {
        when:
        def response = api.get("/api/protech/lookups/$kind?q=$q")

        then:
        response.status == 200
        response.json*.value == values
        response.json.every { it.keySet() == ['value', 'detail'] as Set }

        where:
        kind                | q             || values
        'users'             | 'GRACE'       || ['Grace Mitchell', 'Grace Turner']
        'assignment-groups' | 'fund%20acc'  || ['Fund Accounting Application Support']
        'business-services' | 'custody'     || ['Custody']
        'departments'       | 'custody'     || ['Custody']
        'incidents'         | 'certificate' || ['INC0105126']
        'problems'          | 'nobody'      || []
    }

    def "a lookup without a text answers the first entries up to the limit, and every lookup is logged"() {
        when:
        def users = api.get('/api/protech/lookups/users?limit=3')
        def mateusz = api.get('/api/protech/lookups/users?q=mateusz.matan@')

        then:
        users.status == 200
        users.json*.value == ['Aisha Rahman', 'Ann Lee', 'Charlotte Reed']
        mateusz.json == [[value: 'Mateusz Matan', detail: 'mateusz.matan@bbh.com']]
        callLog()*.subMap('userName', 'operation', 'target', 'outcome') == [
                [userName: USER, operation: 'find', target: 'users 3', outcome: 'SUCCESS'],
                [userName: USER, operation: 'find', target: 'users mateusz.matan@ 20', outcome: 'SUCCESS']]
    }

    def "a lookup of a kind ProTech does not have is not found, and a limit out of range is refused"() {
        when:
        def unknown = api.get('/api/protech/lookups/teams?q=fund')
        def tooMany = api.get('/api/protech/lookups/users?limit=101')

        then:
        unknown.status == 404
        unknown.json.detail == 'Lookup teams does not exist'
        tooMany.status == 400
        tooMany.json.problems == [[field: 'limit', message: 'must be between 1 and 100']]
        callLog() == []
    }
}

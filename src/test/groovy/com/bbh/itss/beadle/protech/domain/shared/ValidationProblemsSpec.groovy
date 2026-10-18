package com.bbh.itss.beadle.protech.domain.shared

import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.support.ChangeFixtures.problemsThrownBy

class ValidationProblemsSpec extends Specification {

    def problems = new ValidationProblems()

    def "a view prefixes the fields it reports with its path"() {
        when:
        problems.add('code', 'is taken')
        problems.at('services[0]').at('build').add('javaPath', 'is required')

        then:
        problemsThrownBy(problems) == [new FieldProblem('code', 'is taken'),
                                       new FieldProblem('services[0].build.javaPath', 'is required')]
    }

    def "a request with #fields.size() problems #outcome"() {
        given:
        fields.each { problems.add(it, 'is taken') }

        when:
        problems.throwIfAny()

        then:
        def e = thrown(InvalidRequestException)
        e.message == message
        e.problems()*.field == fields

        where:
        fields           || message
        ['name']         || 'is taken'
        ['name', 'code'] || '2 fields are invalid'

        outcome = fields.size() == 1 ? 'says it' : 'counts them'
    }

    def "a request without problems passes and a single field problem can be made directly"() {
        when:
        problems.throwIfAny()
        def e = InvalidRequestException.of('range', 'use 30d')

        then:
        noExceptionThrown()
        e.message == 'use 30d'
        e.problems() == [new FieldProblem('range', 'use 30d')]
    }
}

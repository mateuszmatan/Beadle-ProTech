package com.bbh.itss.beadle.protech.adapter.in.web

import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.beadle.protech.domain.shared.RefusedException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.shared.Failures.notFound
import static com.bbh.itss.beadle.protech.support.Json.parse
import static com.bbh.itss.beadle.protech.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ApiExceptionHandlerSpec extends Specification {

    static final FieldProblem PROBLEM = new FieldProblem('services[0].build.javaPath', 'is required')
    static final String UNEXPECTED = 'Beadle-ProTech could not handle the request. The failure is in its log.'

    def handler = new ApiExceptionHandler()

    MockMvc mvc = standaloneSetup(new SampleController()).setControllerAdvice(handler).build()

    def "#failure is #status #title"() {
        expect:
        with(answer(handler)) {
            it.status == status
            it.title == title
            it.detail == detail
            it.properties?.problems == errors
        }

        where:
        failure                    | answer                                                 || status | title
        'a missing record'         | { it.notFound(notFound('Lookup', 'pets')) }                || 404    | 'Not found'
        'a refusal of ProTech'     | { it.conflict(new RefusedException('ProTech has no change CHG1')) } || 409 | 'Conflict'
        'a broken business rule'   | { it.invalid(new InvalidRequestException([PROBLEM])) } || 400   | 'Validation failed'
        'an unexpected failure'    | { it.unexpected(new IllegalStateException('at com.bbh')) } || 500 | 'Request failed'
        'an unreachable ProTech'   | { it.unavailable(new UncheckedIOException('ProTech timed out', new IOException())) } || 502 | 'Bad Gateway'

        detail << ['Lookup pets does not exist', 'ProTech has no change CHG1', 'is required', UNEXPECTED, 'ProTech timed out']
        errors << [null, null, [PROBLEM], null, null]
    }

    def "bean validation errors are listed per field"() {
        when:
        def response = mvc.perform(json(toJson([day: 'FRIDAY', labels: labels]))).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Validation failed'
            it.detail == detail
            problems*.field == fields
        }

        where:
        labels       || detail                   | fields
        []           || 'add at least one label' | ['labels']
        ['a', 'b,c'] || 'must not contain commas' | ['labels[1]']
    }

    def "#request answers #status #title without naming a class or a method"() {
        when:
        def response = mvc.perform(builder).andReturn().response

        then:
        response.status == status
        response.contentType.startsWith('application/problem+json')
        with(parse(response.contentAsString)) {
            it.title == title
            it.detail == detail
            problems == null
        }

        where:
        request                  | builder                                           || status | title                    | detail
        'a failing controller'   | get('/api/samples/7')                             || 500    | 'Request failed'         | UNEXPECTED
        'a failing framework'    | get('/api/samples/broken')                        || 500    | 'Request failed'         | UNEXPECTED
        'a refusal'              | get('/api/samples/refused')                       || 409    | 'Conflict'               | 'ProTech has no change CHG1'
        'no body'                | json('')                                          || 400    | 'Malformed request'      | 'The request body is missing.'
        'a body that is no JSON' | json('{"type":')                                  || 400    | 'Malformed request'      | 'The request body is not valid JSON.'
        'a body of another shape'| json('["FRIDAY"]')                                  || 400    | 'Malformed request'      | 'The request body does not have the shape this endpoint expects.'
        'a path value'           | get('/api/samples/undefined')                     || 400    | 'Malformed request'      | "The value given for 'id' is not one this endpoint can read."
        'a query value'          | get('/api/samples').param('size', 'big')          || 400    | 'Malformed request'      | "The value given for 'size' is not one this endpoint can read."
        'no query value'         | get('/api/samples')                               || 400    | 'Bad Request'            | "Required parameter 'size' is not present."
        'another method'         | post('/api/samples/7')                            || 405    | 'Method Not Allowed'     | "Method 'POST' is not supported."
        'another content type'   | post('/api/samples').contentType(TEXT_PLAIN).content('FRIDAY') || 415 | 'Unsupported Media Type' | "Content-Type 'text/plain' is not supported."
    }

    private static MockHttpServletRequestBuilder json(String body) {
        post('/api/samples').contentType(APPLICATION_JSON).content(body)
    }

    @RestController
    static class SampleController {

        @GetMapping('/api/samples/{id}')
        String read(@PathVariable('id') Long id) {
            throw new UnsupportedOperationException("no sample $id is loaded")
        }

        @GetMapping('/api/samples/broken')
        String broken() {
            throw new IllegalStateException("'contributor' must not be null")
        }

        @GetMapping('/api/samples/refused')
        String refused() {
            throw new RefusedException('ProTech has no change CHG1')
        }

        @GetMapping('/api/samples')
        String list(@RequestParam('size') int size) {
            "$size samples"
        }

        @PostMapping('/api/samples')
        String create(@jakarta.validation.Valid @RequestBody SampleRequest request) {
            request.day().name()
        }
    }
}

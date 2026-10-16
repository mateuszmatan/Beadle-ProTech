package com.bbh.itss.beadle.protech.support

import com.bbh.itss.beadle.protech.adapter.in.web.ApiExceptionHandler
import com.bbh.itss.beadle.protech.adapter.in.web.NormalizedRequestBodies
import com.bbh.itss.beadle.protech.config.ServiceTokenFilter
import com.bbh.itss.beadle.protech.config.ServiceTokenProperties
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.support.Json.jackson
import static com.bbh.itss.beadle.protech.support.Json.parse
import static com.bbh.itss.beadle.protech.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/** A controller behind the service token filter and the error handling of the service, driven through MockMvc. */
abstract class ControllerSpecification extends Specification {

    static final String TOKEN = 'test-service-token'
    static final String AUTHORIZATION = 'Bearer ' + TOKEN

    abstract Object controller()

    MockMvc mvc

    def setup() {
        mvc = standaloneSetup(controller())
                .setControllerAdvice(new ApiExceptionHandler(), new NormalizedRequestBodies())
                .addFilters(new ServiceTokenFilter(new ServiceTokenProperties(TOKEN)))
                .build()
    }

    MockHttpServletResponse call(MockHttpServletRequestBuilder request) {
        mvc.perform(request.header('Authorization', AUTHORIZATION)).andReturn().response
    }

    MockHttpServletResponse callWithout(MockHttpServletRequestBuilder request, String authorization) {
        mvc.perform(authorization == null ? request : request.header('Authorization', authorization))
                .andReturn().response
    }

    /** The request with a body made by Groovy from maps and lists. */
    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        request.contentType(APPLICATION_JSON).content(toJson(body))
    }

    /** The request with a body Jackson makes of records, as Beadle-GUI sends them. */
    static MockHttpServletRequestBuilder records(MockHttpServletRequestBuilder request, Object body) {
        request.contentType(APPLICATION_JSON).content(jackson(body))
    }

    static Object bodyOf(MockHttpServletResponse response) {
        parse(response.contentAsString)
    }

    static List<String> unauthorized() {
        [null, 'Bearer wrong-token', 'Bearer ', 'Basic dGVzdA==', TOKEN]
    }
}

package com.bbh.itss.beadle.protech.support

import groovy.transform.Canonical
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.ResponseCreator
import org.springframework.web.client.RestClient

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import static org.springframework.http.HttpStatus.ACCEPTED
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import static org.springframework.test.web.client.ExpectedCount.manyTimes
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus

/**
 * Stands in for Beadle-GUI: a {@link MockRestServiceServer} bound to the {@link RestClient.Builder} of the service
 * takes every callback, answers {@code 202}, or {@code 503} as many times as a spec asked for a change, runs what a
 * spec asked to do when a change's callback arrives (Beadle-GUI reads the change again before it answers), and keeps
 * every request it got.
 */
class GuiCallbacks {

    final RestClient.Builder builder = RestClient.builder()
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
    private final List<Callback> received = new CopyOnWriteArrayList<>()
    private final Map<String, Integer> refusals = new ConcurrentHashMap<>()
    private final Map<String, Closure> actions = new ConcurrentHashMap<>()

    GuiCallbacks() {
        server.expect(manyTimes(), { ClientHttpRequest request -> } as RequestMatcher)
                .andRespond({ ClientHttpRequest request -> answer(request as MockClientHttpRequest) } as ResponseCreator)
    }

    List<Callback> callbacksFor(String number) {
        received.findAll { it.body?.number == number }
    }

    void refuse(String number, int times) {
        refusals[number] = times
    }

    /** Runs {@code action} whenever a callback for {@code number} arrives, before it is answered, as Beadle-GUI does. */
    void onCallback(String number, Closure action) {
        actions[number] = action
    }

    private answer(MockClientHttpRequest request) {
        def body = Json.parse(request.bodyAsString) as Map
        int status = refused(body?.number as String) ? SERVICE_UNAVAILABLE.value() : ACCEPTED.value()
        if (status == ACCEPTED.value() && body?.number != null) {
            actions[body.number as String]?.call(body)
        }
        received << new Callback(request.method.name(), request.URI.toString(),
                request.headers.toSingleValueMap().collectEntries { name, value -> [name.toLowerCase(), value] },
                body, status)
        withStatus(status == ACCEPTED.value() ? ACCEPTED : SERVICE_UNAVAILABLE).createResponse(request)
    }

    private synchronized boolean refused(String number) {
        Integer left = number == null ? null : refusals[number]
        if (!left) {
            return false
        }
        refusals[number] = left - 1
        true
    }

    @Canonical
    static class Callback {
        String method
        String url
        Map<String, String> headers
        Map body
        int status

        String header(String name) {
            headers[name.toLowerCase()]
        }
    }
}

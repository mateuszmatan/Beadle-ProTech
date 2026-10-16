package com.bbh.itss.beadle.protech.support

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import tools.jackson.databind.json.JsonMapper

final class Json {

    private static final JsonMapper JACKSON = JsonMapper.builder().build()

    private Json() {
    }

    static String toJson(Object body) {
        JsonOutput.toJson(body)
    }

    /** The JSON Jackson makes of a record, as Beadle-GUI sends its domain records. */
    static String jackson(Object value) {
        JACKSON.writeValueAsString(value)
    }

    /** A record as Jackson reads it from JSON, as Beadle-GUI reads the answers. */
    static <T> T jackson(String json, Class<T> type) {
        JACKSON.readValue(json, type)
    }

    static Object parse(String json) {
        json ? new JsonSlurper().parseText(json) : null
    }
}

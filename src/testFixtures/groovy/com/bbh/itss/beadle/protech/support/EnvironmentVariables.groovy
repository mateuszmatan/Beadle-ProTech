package com.bbh.itss.beadle.protech.support

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME

/**
 * Environment variables for the application of a spec: the ones of the process and the given ones, in the property
 * source of the system environment, so that Spring reads them as it reads a shell or a ConfigMap, relaxed binding
 * included ({@code BEADLE_PROTECH_URL} binds {@code beadle.protech.url}). As an initializer it sets
 * {@link #SHARED_WITH_GUI}.
 */
class EnvironmentVariables implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * What a shell, an env file or a ConfigMap shared with Beadle-GUI holds for it (docs/CONTRACTS.md): where it finds
     * Beadle-Internal and Beadle-ProTech, the local defaults.
     */
    static final Map<String, String> SHARED_WITH_GUI = [BEADLE_INTERNAL_URL: 'http://localhost:8082',
                                                        BEADLE_PROTECH_URL : 'http://localhost:8083'].asImmutable()

    @Override
    void initialize(ConfigurableApplicationContext context) {
        set(context.environment, SHARED_WITH_GUI)
    }

    /** A fresh environment with {@code variables} among its environment variables. */
    static ConfigurableEnvironment environment(Map<String, String> variables) {
        def environment = new StandardEnvironment()
        set(environment, variables)
        environment
    }

    /** Adds {@code variables} to the environment variables of {@code environment}. */
    static void set(ConfigurableEnvironment environment, Map<String, String> variables) {
        def current = environment.propertySources.get(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)?.source
        Map<String, Object> merged = new LinkedHashMap<>(current instanceof Map ? current : System.getenv())
        merged.putAll(variables)
        environment.propertySources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, merged))
    }
}

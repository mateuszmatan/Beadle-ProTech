package com.bbh.itss.beadle.protech.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Keeps the {@code oracle} profiles (rd, qc and prod) on the real ProTech. While {@code beadle.protech.instance-url}
 * ({@code PROTECH_URL}) is blank the demo ProTech plays, and it answers like the real one: it hands Beadle-GUI made-up
 * CHG and CTASK numbers, which Beadle-GUI records as changes raised. So these profiles refuse to start while
 * {@code PROTECH_URL} is blank, unless {@code beadle.protech.demo.enabled} ({@code PROTECH_DEMO=true}) asks for the
 * demo on purpose. Beadle-GUI's {@code BEADLE_PROTECH_URL} does not count: it names this service, not ProTech. The
 * check runs before any bean is created, so nothing reaches the database or Beadle-GUI first.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile("oracle")
public class RealProTechConfiguration {

    static final String INSTANCE_URL = "beadle.protech.instance-url";
    static final String DEMO_ENABLED = "beadle.protech.demo.enabled";

    @Bean
    static BeanFactoryPostProcessor realProTechCheck(Environment environment) {
        return beanFactory -> requireRealProTech(environment);
    }

    static void requireRealProTech(Environment environment) {
        if (!isBlank(environment.getProperty(INSTANCE_URL))) {
            return;
        }
        if (environment.getProperty(DEMO_ENABLED, Boolean.class, false)) {
            log.warn("ProTech is played by the demo adapter: PROTECH_URL is blank and PROTECH_DEMO=true, so the changes "
                    + "Beadle-GUI raises are made up");
            return;
        }
        throw new IllegalStateException("The rd, qc and prod profiles need the real ProTech, but PROTECH_URL is blank "
                + "and the demo ProTech would answer in its place. Set PROTECH_URL, PROTECH_USERNAME and "
                + "PROTECH_PASSWORD in the secret beadle-protech-servicenow, or PROTECH_DEMO=true to run on the demo "
                + "ProTech on purpose.");
    }
}

package com.bbh.itss.beadle.protech.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Keeps the {@code oracle} profiles (rd, qc and prod) on the real ProTech: the demo ProTech, which plays while
 * {@code beadle.protech.instance-url} ({@code PROTECH_URL}) is blank, is for a laptop only, since it hands Beadle-GUI
 * made-up CHG and CTASK numbers. Beadle-GUI's {@code BEADLE_PROTECH_URL} does not count: it names this service, not
 * ProTech. The check runs before any bean is created, so nothing reaches the database or Beadle-GUI first.
 */
@Configuration(proxyBeanMethods = false)
@Profile("oracle")
public class RealProTechConfiguration {

    @Bean
    static BeanFactoryPostProcessor realProTechCheck(Environment environment) {
        return beanFactory -> {
            if (isBlank(environment.getProperty("beadle.protech.instance-url"))) {
                throw new IllegalStateException("The rd, qc and prod profiles need the real ProTech, but PROTECH_URL "
                        + "is blank. Set PROTECH_URL, PROTECH_USERNAME and PROTECH_PASSWORD in the secret "
                        + "beadle-protech-account.");
            }
        };
    }
}

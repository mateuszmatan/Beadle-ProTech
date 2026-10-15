package com.bbh.itss.beadle.protech.adapter.out.protech;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where ProTech is and how to sign in; without a {@code url} the demo ProTech plays its part. {@code poll} is how
 * often the live adapter asks ProTech what moved.
 */
@ConfigurationProperties("beadle.protech")
public record ProTechProperties(String url, String username, String password, @DefaultValue("PT1M") Duration poll) {

    static final String CONFIGURED = "'${beadle.protech.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.protech.url:}' == ''";
}

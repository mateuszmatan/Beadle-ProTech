package com.bbh.itss.beadle.protech.adapter.out.protech;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * Where ProTech is and how to sign in; without a {@code url} the demo ProTech plays its part. {@code username} and
 * {@code password} are the Beadle service account of ProTech, {@code poll} is how often the live adapter asks
 * ProTech what moved and {@code fields} names the tables, fields and state values of ProTech.
 */
@ConfigurationProperties("beadle.protech")
public record ProTechProperties(String url, String username, String password, @DefaultValue("PT1M") Duration poll,
                                ProTechFields fields) {

    static final String CONFIGURED = "'${beadle.protech.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.protech.url:}' == ''";
    static final Duration DEFAULT_POLL = Duration.ofMinutes(1);

    public ProTechProperties {
        poll = getIfNull(poll, DEFAULT_POLL);
        fields = getIfNull(fields, ProTechFields.DEFAULTS);
    }
}

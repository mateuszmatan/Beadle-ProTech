package com.bbh.itss.beadle.protech.adapter.out.protech;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * Where ProTech is and how to sign in; without an {@code instanceUrl} the demo ProTech plays its part.
 * {@code username} and {@code password} are the Beadle service account of ProTech and {@code fields} names the
 * tables, fields and state values of ProTech. The address is {@code beadle.protech.instance-url}, not {@code beadle.protech.url}: Spring binds the environment variable
 * {@code BEADLE_PROTECH_URL} to {@code beadle.protech.url}, and that variable is where Beadle-GUI finds Beadle-ProTech
 * (docs/CONTRACTS.md). In a shell, an env file or a ConfigMap shared with Beadle-GUI it would switch Beadle-ProTech to
 * the live adapter, pointed at itself.
 */
@ConfigurationProperties("beadle.protech")
public record ProTechProperties(String instanceUrl, String username, String password, ProTechFields fields) {

    static final String INSTANCE_URL = "beadle.protech.instance-url";
    static final String CONFIGURED = "'${" + INSTANCE_URL + ":}' != ''";
    static final String NOT_CONFIGURED = "'${" + INSTANCE_URL + ":}' == ''";

    public ProTechProperties {
        fields = getIfNull(fields, ProTechFields.DEFAULTS);
    }
}

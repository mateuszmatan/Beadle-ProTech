package com.bbh.itss.beadle.protech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The shared secret Beadle-GUI and the services send as {@code Authorization: Bearer <token>}. */
@ConfigurationProperties("beadle.services")
public record ServiceTokenProperties(String token) {
}

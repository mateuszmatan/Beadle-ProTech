package com.bbh.itss.beadle.protech.adapter.out.protech;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How the demo ProTech behaves: {@code applyDelay} is how long an update takes to show when a change is read back,
 * so that a GUI can show it being applied.
 */
@ConfigurationProperties("beadle.protech.demo")
public record DemoProTechProperties(@DefaultValue("PT3S") Duration applyDelay) {
}

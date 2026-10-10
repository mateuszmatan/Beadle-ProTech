package com.bbh.itss.dso.portal.adapter.out.servicenow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("dso.demo")
public record DemoProTechProperties(@DefaultValue("PT3S") Duration protechApplyDelay) {
}

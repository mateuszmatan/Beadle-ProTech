package com.bbh.itss.beadle.protech.config;

import com.bbh.itss.beadle.protech.application.UseCase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.springframework.context.annotation.FilterType.ANNOTATION;

/**
 * Makes a bean of every {@link UseCase}. A use case runs without a transaction: it calls ProTech and Beadle-GUI,
 * and each write of the persistence adapters commits on its own.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UseCase.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = ANNOTATION, classes = UseCase.class))
public class UseCaseConfiguration {
}

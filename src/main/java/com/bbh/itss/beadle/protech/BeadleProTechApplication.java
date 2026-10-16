package com.bbh.itss.beadle.protech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BeadleProTechApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeadleProTechApplication.class, args);
    }
}

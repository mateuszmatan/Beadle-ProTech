package com.bbh.itss.dso.portal.domain.change;

public record ChangeTask(String number, String serviceName, String shortDescription, String description) {

    ChangeTask numbered(String number) {
        return new ChangeTask(number, serviceName, shortDescription, description);
    }
}

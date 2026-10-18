package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.NotFoundException;

import static java.util.Arrays.stream;
import static java.util.Locale.ROOT;

public enum LookupKind {
    USERS, BUSINESS_SERVICES, ASSIGNMENT_GROUPS, RELEASES, CONFIGURATION_ITEMS, INCIDENTS, PROBLEMS, CLIENTS;

    public static LookupKind of(String path) {
        return stream(values()).filter(kind -> kind.path().equals(path)).findFirst()
                .orElseThrow(() -> new NotFoundException("Lookup " + path + " does not exist"));
    }

    public String path() {
        return name().toLowerCase(ROOT).replace('_', '-');
    }
}

package com.bbh.itss.dso.portal.domain.change;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.util.Arrays.stream;
import static java.util.Locale.ROOT;

public enum LookupKind {
    USERS, DEPARTMENTS, ASSIGNMENT_GROUPS, RELEASES, CONFIGURATION_ITEMS, INCIDENTS, PROBLEMS, CLIENTS;

    public static LookupKind of(String path) {
        return stream(values()).filter(kind -> kind.path().equals(path)).findFirst()
                .orElseThrow(() -> notFound("Lookup", path));
    }

    public String path() {
        return name().toLowerCase(ROOT).replace('_', '-');
    }
}

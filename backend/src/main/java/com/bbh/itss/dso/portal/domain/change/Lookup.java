package com.bbh.itss.dso.portal.domain.change;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.Strings.CI;

public record Lookup(String value, String detail) {

    public static final int MAX_LOOKUPS = 20;

    public boolean matches(String query) {
        return isEmpty(query) || CI.contains(value, query) || CI.contains(detail, query);
    }
}

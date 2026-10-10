package com.bbh.itss.dso.portal.adapter.out.servicenow;

import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoProTechLookups.USERS;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.RELEASE_MANAGEMENT;
import static java.lang.Math.floorMod;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Locale.ROOT;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@NoArgsConstructor(access = PRIVATE)
final class DemoApprovers {

    static final Map<String, List<String>> GROUPS = new TreeMap<>(CASE_INSENSITIVE_ORDER);

    static {
        GROUPS.put(RELEASE_MANAGEMENT, List.of("Rebecca Lawson", "Thomas Ashby"));
        GROUPS.put("Database Administration", List.of("Henry Collins", "Kenji Watanabe"));
        GROUPS.put("Cloud Engineering", List.of("Daniel Foster", "Priya Natarajan"));
        GROUPS.put("Data Movement - API", List.of("Charlotte Reed", "Emma Brooks"));
        GROUPS.put("Middleware Support", List.of("William Hayes", "Ann Lee"));
        GROUPS.put("Network Operations", List.of("Samuel Price", "Grace Mitchell"));
        GROUPS.put("OIS Support", List.of("Michael Grant", "Hannah Whitfield"));
        GROUPS.put("OpenShift Platform Support", List.of("Marcus Webb", "Sophia Turner"));
        GROUPS.put("Security Operations", List.of("Laura Kingsley", "Robert Ellison"));
        GROUPS.put("Service Desk", List.of("Jane Smith"));
    }

    static List<String> of(String group) {
        String name = trimToNull(group);
        if (name == null) {
            return List.of();
        }
        List<String> known = GROUPS.get(name);
        if (known != null) {
            return known;
        }
        int first = floorMod(name.toLowerCase(ROOT).hashCode(), USERS.size());
        return List.of(USERS.get(first), USERS.get((first + USERS.size() / 2) % USERS.size()));
    }
}

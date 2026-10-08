package com.bbh.itss.dso.portal.domain.change;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.Versions.requireReadAt;
import static java.util.Objects.requireNonNull;

public record ChangeProfile(long productId, ChangeTemplate template, List<TaskText> tasks, long version,
                            Instant updatedAt) {

    public ChangeProfile {
        requireNonNull(template, "a change profile needs its template");
        tasks = List.copyOf(tasks);
    }

    public static ChangeProfile create(long productId, ChangeTemplate template, List<TaskText> tasks) {
        return new ChangeProfile(productId, template, tasks, 0, null);
    }

    public ChangeProfile change(Long expectedVersion, ChangeTemplate template, List<TaskText> tasks) {
        requireReadAt(expectedVersion, version);
        return new ChangeProfile(productId, template, tasks, version, updatedAt);
    }
}

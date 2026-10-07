package com.bbh.itss.dso.portal.domain.change;

import java.time.Instant;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static com.bbh.itss.dso.portal.domain.shared.Versions.requireCurrent;
import static java.util.Objects.requireNonNull;

public record ChangeProfile(long productId, ChangeTemplate template, long version, Instant updatedAt) {

    public ChangeProfile {
        requireNonNull(template, "a change profile needs its template");
    }

    public static ChangeProfile create(long productId, ChangeTemplate template) {
        return new ChangeProfile(productId, template, 0, null);
    }

    public ChangeProfile change(Long expectedVersion, ChangeTemplate template) {
        if (expectedVersion == null) {
            throw staleVersion();
        }
        requireCurrent(expectedVersion, version);
        return new ChangeProfile(productId, template, version, updatedAt);
    }
}

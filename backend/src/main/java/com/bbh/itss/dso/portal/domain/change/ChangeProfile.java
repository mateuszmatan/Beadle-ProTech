package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ConflictException;
import com.bbh.itss.dso.portal.domain.shared.Versions;

import java.time.Instant;
import java.util.Objects;

public record ChangeProfile(long productId, ChangeTemplate template, long version, Instant updatedAt) {

    public ChangeProfile {
        Objects.requireNonNull(template, "a change profile needs its template");
    }

    public static ChangeProfile create(long productId, ChangeTemplate template) {
        return new ChangeProfile(productId, template, 0, null);
    }

    public ChangeProfile change(Long expectedVersion, ChangeTemplate template) {
        if (expectedVersion == null) {
            throw ConflictException.staleVersion();
        }
        Versions.requireCurrent(expectedVersion, version);
        return new ChangeProfile(productId, template, version, updatedAt);
    }
}

package com.bbh.itss.dso.portal.domain.change;

import java.time.Instant;

public record ChangeProfileSummary(long productId, String productName, long version, Instant updatedAt) {
}

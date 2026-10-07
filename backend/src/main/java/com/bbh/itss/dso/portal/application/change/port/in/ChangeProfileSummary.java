package com.bbh.itss.dso.portal.application.change.port.in;

import java.time.Instant;

public record ChangeProfileSummary(long productId, String productName, long version, Instant updatedAt) {
}

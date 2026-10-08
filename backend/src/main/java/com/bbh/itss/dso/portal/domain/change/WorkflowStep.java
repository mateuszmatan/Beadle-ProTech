package com.bbh.itss.dso.portal.domain.change;

import java.time.Instant;

public record WorkflowStep(ChangeState state, Instant enteredAt) {
}

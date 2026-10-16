package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;

public record WorkflowStep(ChangeState state, Instant enteredAt) {
}

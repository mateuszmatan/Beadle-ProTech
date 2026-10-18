package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_PROTECH_CHANGE")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
class TrackedChangeEntity {

    @Id
    private String changeNumber;

    @Enumerated(STRING)
    private ChangeState state;

    private Instant lastSeenAt;

    private Instant lastEventAt;

    private String correlationId;

    static TrackedChangeEntity of(TrackedChange change) {
        return new TrackedChangeEntity(change.number(), change.state(), change.lastSeenAt(), change.lastEventAt(),
                change.correlationId());
    }

    TrackedChange toDomain() {
        return new TrackedChange(changeNumber, state, lastSeenAt, lastEventAt, correlationId);
    }
}

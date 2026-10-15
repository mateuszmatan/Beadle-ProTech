package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_PROTECH_CHANGE")
@NoArgsConstructor(access = PROTECTED)
class TrackedChangeEntity {

    @Id
    private String changeNumber;

    @Enumerated(STRING)
    private ChangeState state;

    private Instant lastSeenAt;

    private Instant lastEventAt;

    private String correlationId;

    static TrackedChangeEntity of(TrackedChange change) {
        TrackedChangeEntity entity = new TrackedChangeEntity();
        entity.changeNumber = change.number();
        entity.state = change.state();
        entity.lastSeenAt = change.lastSeenAt();
        entity.lastEventAt = change.lastEventAt();
        entity.correlationId = change.correlationId();
        return entity;
    }

    TrackedChange toDomain() {
        return new TrackedChange(changeNumber, state, lastSeenAt, lastEventAt, correlationId);
    }
}

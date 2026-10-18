package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.protech.domain.calllog.Outcome;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;
import static org.hibernate.type.SqlTypes.NUMERIC;

/** A row of the call log; its ids and durations are NUMBER(19) columns, NUMERIC to JDBC on Oracle and H2 alike. */
@Entity
@Table(name = "BEADLE_PROTECH_CALL_LOG")
@NoArgsConstructor(access = PROTECTED)
class CallLogEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @JdbcTypeCode(NUMERIC)
    private Long id;

    private Instant at;

    private String userName;

    private String correlationId;

    @Enumerated(STRING)
    private ExternalSystem system;

    private String operation;

    private String target;

    @Enumerated(STRING)
    private Outcome outcome;

    private String message;

    @JdbcTypeCode(NUMERIC)
    private long durationMs;

    static CallLogEntity of(CallLogEntry entry) {
        CallLogEntity entity = new CallLogEntity();
        entity.at = entry.at();
        entity.userName = entry.userName();
        entity.correlationId = entry.correlationId();
        entity.system = entry.system();
        entity.operation = entry.operation();
        entity.target = entry.target();
        entity.outcome = entry.outcome();
        entity.message = entry.message();
        entity.durationMs = entry.durationMs();
        return entity;
    }

    CallLogEntry toDomain() {
        return new CallLogEntry(id, at, userName, correlationId, system, operation, target, outcome, message,
                durationMs);
    }
}

package com.bbh.itss.beadle.protech.support

import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry
import com.bbh.itss.beadle.protech.domain.change.TrackedChange

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.PROTECH
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.SUCCESS
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.copy

class CallLogFixtures {

    static final Instant CALLED = Instant.parse('2026-10-15T09:00:00Z')

    static CallLogEntry callLogEntry(Map changes = [:]) {
        copy(changes, new CallLogEntry(1L, CALLED, 'Mateusz Matan', 'corr-1', PROTECH, 'raise',
                'CHG0031001 CertScanner CERT 4.2: Expiry alerts', SUCCESS, null, 12L))
    }

    static TrackedChange trackedChange(Map changes = [:]) {
        copy(changes, new TrackedChange('CHG0031001', DRAFT, CALLED, CALLED, 'corr-1'))
    }
}

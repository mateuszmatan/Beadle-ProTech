package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort;
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Slf4j
@Component
class TrackedChangePersistenceAdapter implements TrackedChangePort {

    /** Oracle takes at most 1000 values in an IN list. */
    static final int IN_LIST_MAX = 500;

    private final TrackedChangeJpaRepository changes;
    private final TransactionTemplate ownTransaction;

    TrackedChangePersistenceAdapter(TrackedChangeJpaRepository changes,
                                    PlatformTransactionManager transactionManager) {
        this.changes = changes;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void track(Collection<TrackedChange> seen) {
        for (TrackedChange change : seen) {
            try {
                ownTransaction.executeWithoutResult(status -> merge(change, true));
            } catch (RuntimeException e) {
                log.error("Change {} cannot be followed: Beadle-GUI will not hear when ProTech moves it",
                        change.number(), e);
            }
        }
    }

    @Override
    public void touch(String number, Instant at, String correlationId) {
        try {
            ownTransaction.executeWithoutResult(status ->
                    merge(TrackedChange.touched(number, at, correlationId), false));
        } catch (RuntimeException e) {
            log.warn("Change {} was changed by Beadle-GUI but this cannot be noted: Beadle-GUI may hear of its own "
                    + "change", number, e);
        }
    }

    @Override
    public Optional<TrackedChange> find(String number) {
        return changes.findById(number).map(TrackedChangeEntity::toDomain);
    }

    @Override
    public List<TrackedChange> watched() {
        return changes.findByStateIsNullOrStateNotOrderByChangeNumberAsc(CLOSED).stream()
                .map(TrackedChangeEntity::toDomain).toList();
    }

    @Override
    public void reported(TrackedChange looked, ChangeActivity activity, Instant at) {
        ownTransaction.executeWithoutResult(status -> {
            TrackedChange current = changes.lockById(looked.number()).map(TrackedChangeEntity::toDomain)
                    .orElse(looked);
            changes.save(TrackedChangeEntity.of(current.reported(looked, activity, at)));
        });
    }

    @Override
    public void seen(Collection<String> numbers, Instant at) {
        ownTransaction.executeWithoutResult(status ->
                partition(List.copyOf(numbers), IN_LIST_MAX).forEach(batch -> changes.seen(batch, at)));
    }

    private void merge(TrackedChange change, boolean follow) {
        Optional<TrackedChange> known = changes.lockById(change.number()).map(TrackedChangeEntity::toDomain);
        if (known.isPresent() || follow) {
            changes.save(TrackedChangeEntity.of(known.map(found -> found.updatedBy(change)).orElse(change)));
        }
    }
}

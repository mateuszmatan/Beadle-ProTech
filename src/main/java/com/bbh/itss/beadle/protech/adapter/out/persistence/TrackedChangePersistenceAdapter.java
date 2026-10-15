package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.Optional;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Slf4j
@Component
class TrackedChangePersistenceAdapter implements TrackedChangePort {

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
                ownTransaction.executeWithoutResult(status -> changes.save(TrackedChangeEntity.of(
                        changes.findById(change.number()).map(TrackedChangeEntity::toDomain)
                                .map(known -> known.updatedBy(change)).orElse(change))));
            } catch (RuntimeException e) {
                log.error("Change {} cannot be followed: Beadle-GUI will not hear when ProTech moves it",
                        change.number(), e);
            }
        }
    }

    @Override
    public Optional<TrackedChange> find(String number) {
        return changes.findById(number).map(TrackedChangeEntity::toDomain);
    }
}

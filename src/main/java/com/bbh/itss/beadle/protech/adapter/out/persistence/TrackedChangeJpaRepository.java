package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

interface TrackedChangeJpaRepository extends Repository<TrackedChangeEntity, String> {

    TrackedChangeEntity save(TrackedChangeEntity entity);

    Optional<TrackedChangeEntity> findById(String number);

    /** The change, locked until the transaction ends, so that two merges of the same change do not interleave. */
    @Lock(PESSIMISTIC_WRITE)
    @Query("select c from TrackedChangeEntity c where c.changeNumber = :number")
    Optional<TrackedChangeEntity> lockById(@Param("number") String number);

    List<TrackedChangeEntity> findByStateIsNullOrStateNotOrderByChangeNumberAsc(ChangeState closed);

    @Modifying
    @Query("update TrackedChangeEntity c set c.lastSeenAt = :at where c.changeNumber in :numbers"
            + " and (c.lastSeenAt is null or c.lastSeenAt < :at)")
    int seen(@Param("numbers") Collection<String> numbers, @Param("at") Instant at);
}

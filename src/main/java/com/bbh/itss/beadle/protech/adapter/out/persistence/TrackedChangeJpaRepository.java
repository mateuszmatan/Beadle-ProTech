package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.domain.change.ChangeState;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TrackedChangeJpaRepository extends Repository<TrackedChangeEntity, String> {

    TrackedChangeEntity save(TrackedChangeEntity entity);

    Optional<TrackedChangeEntity> findById(String number);

    List<TrackedChangeEntity> findByStateIsNullOrStateNotOrderByChangeNumberAsc(ChangeState closed);

    @Modifying
    @Query("update TrackedChangeEntity c set c.lastSeenAt = :at where c.changeNumber in :numbers")
    int seen(@Param("numbers") Collection<String> numbers, @Param("at") Instant at);
}

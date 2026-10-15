package com.bbh.itss.beadle.protech.adapter.out.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

interface TrackedChangeJpaRepository extends Repository<TrackedChangeEntity, String> {

    TrackedChangeEntity save(TrackedChangeEntity entity);

    Optional<TrackedChangeEntity> findById(String number);
}

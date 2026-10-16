package com.bbh.itss.beadle.protech.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

interface CallLogJpaRepository extends Repository<CallLogEntity, Long>, JpaSpecificationExecutor<CallLogEntity> {

    CallLogEntity save(CallLogEntity entity);
}

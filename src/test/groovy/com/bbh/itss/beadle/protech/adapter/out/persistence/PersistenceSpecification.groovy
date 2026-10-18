package com.bbh.itss.beadle.protech.adapter.out.persistence

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

/** The persistence adapters on H2 in Oracle mode, migrated by Liquibase: one JPA context shared by their specs. */
@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:persistence;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([CallLogPersistenceAdapter, TrackedChangePersistenceAdapter])
abstract class PersistenceSpecification extends Specification {

    @Autowired
    JdbcTemplate jdbc
}

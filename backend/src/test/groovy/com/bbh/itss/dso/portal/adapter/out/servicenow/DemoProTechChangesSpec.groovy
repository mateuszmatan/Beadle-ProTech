package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.dso.portal.domain.change.ChangeProduct
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate
import com.bbh.itss.dso.portal.domain.change.JiraVersion
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoProTechChanges.MOVED_SCHEDULE
import static com.bbh.itss.dso.portal.adapter.out.servicenow.DemoProTechChanges.SCENES
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.ESCALATED_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.APPLIED
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.NOT_APPLIED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED as TASK_CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeProduct
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.product
import static java.time.Clock.fixed
import static java.time.Duration.ofHours
import static java.time.Duration.ofMinutes
import static java.time.Duration.ofSeconds
import static java.time.ZoneOffset.UTC

class DemoProTechChangesSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-08T12:00:00Z')
    static final List<ChangeProduct> CATALOGUE = [summary(2, 'Payments', 'Custody', 4L),
                                                  summary(4, 'Orphan', null, null),
                                                  summary(1, 'CertScanner', 'Corporate Technology', 3L),
                                                  summary(5, 'Treasury Hub', 'Fund Services', 5L),
                                                  summary(3, 'Atlas', 'Corporate Technology', 3L)]

    ChangeProductsPort products = Stub()
    ChangeProfilesUseCase profiles = Stub()
    ProductionChangesUseCase changes = Stub()
    ProductionChangeRepositoryPort repository = Mock()
    def clock = fixed(NOW, UTC)
    def seeder = new DemoProTechChanges(products, profiles, changes, repository, new DemoServiceNowAdapter(clock,
            new DemoProTechProperties(ofSeconds(3))), clock)
    List<ProductionChange> stored = []

    def setup() {
        profiles.get(_) >> { long id -> ChangeProfileView.builder().productId(id).template(template(downtime: id == 5L))
                .tasks(tasks()).build() }
        changes.versions(_, null) >> [new JiraVersion('CERT 4.3', false, LocalDate.parse('2026-11-01')),
                                      new JiraVersion('CERT 4.2', true, LocalDate.parse('2026-09-01'))]
        changes.epics(_, _, null) >> [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail'),
                                      epic('CERT-9', 'Reports')]
        changes.stories(_, _, ['CERT-1', 'CERT-5'], null) >> [story('CERT-2', 'E-mail the owner', 'CERT-1')]
        changes.preview(_) >> { ChangeCommand command ->
            ProductionChange.draft(changeProduct(id: command.productId(),
                    name: "Product ${command.productId()}".toString()), 'Mateusz Matan', command.fixVersion(),
                    command.schedule(), command.template(),
                    command.epicKeys().collect { epic(it, it) }, command.storyKeys().collect { story(it, it, 'CERT-1') },
                    null, null)
        }
    }

    def "the demo changes spread over the whole ProTech workflow of the products in a department"() {
        given:
        repository.findAll() >> []
        products.findAll() >> CATALOGUE

        when:
        seeder.store()

        then:
        12 * repository.save(_) >> { ProductionChange change ->
            stored << change
            change
        }
        stored*.state() == [CLOSED, CLOSED, CLOSED, IMPLEMENTATION, IMPLEMENTATION, IMPLEMENTATION,
                            ESCALATED_APPROVAL, CTASK_APPROVAL, SECONDARY_APPROVAL, PRIMARY_APPROVAL,
                            BUSINESS_APPROVAL, DRAFT]
        stored*.productId() == [3L, 1L, 2L, 5L] * 3
        stored*.number().every { it ==~ /CHG\d{7}/ }
        stored*.number().toSet().size() == 12
        stored.every { it.tasks().size() == 2 && it.tasks()*.number().every { number -> number ==~ /CTASK\d{7}/ } }
        stored.every { it.syncedAt() == NOW && it.workflow().first() == new WorkflowStep(DRAFT, it.createdAt()) }
        stored.every { it.workflow().last().state() == it.state() && it.id() == null }
        stored*.createdAt() == SCENES.collect { NOW.minus(it.raisedAgo()) }
        stored*.schedule()*.installationStart() == SCENES.collect { NOW.plus(it.startIn()) }
        stored*.fixVersion() == ['CERT 4.2'] * 4 + ['CERT 4.3'] * 8
        stored.every { it.epicKeys() == ['CERT-1', 'CERT-5'] && it.storyKeys() == ['CERT-2'] }
        stored[0].tasks()*.state() == [TASK_CLOSED] * 2
        stored[3].tasks()*.state() == [WORK_IN_PROGRESS] * 2
        stored[4].tasks()*.state() == [OPEN] * 2
        stored[3].update() == new ChangeUpdate(NOT_APPLIED, NOW.minus(ofMinutes(30)), 'Corporate Technology',
                MOVED_SCHEDULE, NOT_APPLIED_MESSAGE, NOW.minus(ofMinutes(29)))
        stored[4].update() == new ChangeUpdate(APPLIED, stored[4].createdAt().plus(ofHours(5)),
                'Corporate Technology', [], null, stored[4].createdAt().plus(ofHours(5)).plusSeconds(3))
        stored.findAll { it.update() != null }.size() == 2
        stored.every { it.openedBy() == 'Mateusz Matan' }
        stored.findAll { it.productId() == 5L }*.schedule().every {
            it.downtimeStart() == it.installationStart() && it.downtimeEnd() == it.installationEnd()
        }
        stored.findAll { it.productId() != 5L }*.schedule().every { it.downtimeStart() == null && it.downtimeEnd() == null }
    }

    def "a database from an earlier version gets the demo changes of the products that have none"() {
        given:
        repository.findAll() >> [raised(productId: 3L), raised(productId: 2L), raised(productId: null)]
        products.findAll() >> CATALOGUE

        when:
        seeder.store()

        then:
        6 * repository.save(_) >> { ProductionChange change ->
            stored << change
            change
        }
        stored*.productId() == [1L, 5L] * 3
        stored*.createdAt() == [1, 3, 5, 7, 9, 11].collect { NOW.minus(SCENES[it].raisedAgo()) }
    }

    def "no demo change is stored when #reason"() {
        given:
        repository.findAll() >> listed
        products.findAll() >> catalogue

        when:
        seeder.store()

        then:
        0 * repository.save(_)

        where:
        reason                          | listed                  | catalogue
        'every product has a change'    | [raised(productId: 1L)] | [summary(1, 'CertScanner', 'Corporate Technology', 3L)]
        'the catalogue is empty'        | []                      | []
        'no product has a department'   | []                      | [summary(4, 'Orphan', null, null)]
    }

    static ChangeProduct summary(long id, String name, String department, Long departmentId) {
        new ChangeProduct(id, name.toUpperCase(), name, null, departmentId, department, null)
    }
}

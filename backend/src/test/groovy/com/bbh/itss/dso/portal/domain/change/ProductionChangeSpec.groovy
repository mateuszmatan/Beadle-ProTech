package com.bbh.itss.dso.portal.domain.change

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.descriptionOf
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.draft
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.shortDescriptionOf
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ProductionChangeSpec extends Specification {

    static final ChangeWindow WINDOW = new ChangeWindow(Instant.parse('2026-10-10T06:00:00Z'),
            Instant.parse('2026-10-10T10:00:00Z'))

    def product = product(code: 'CERTSCANNER', services: [
            [id: 10, name: 'gui', description: 'Angular front end'],
            [id: 11, name: 'backend-api', deployment: deployment(target: OPENSHIFT, appName: 'api',
                    artifactName: 'api')]])
    def epics = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]
    def stories = [story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record each change', 'CERT-5'),
                   story('CERT-3', 'Teams alert', 'CERT-1')]

    def "a draft writes the short description, the description and a task per service from Jira"() {
        when:
        def change = draft(product, 'Corporate Technology', product.services(), template(), WINDOW, epics, stories,
                ' ', null)

        then:
        [change.id(), change.number(), change.url(), change.createdAt()] == [null] * 4
        [change.productId(), change.productCode(), change.productName(), change.departmentName()] ==
                [1L, 'CERTSCANNER', 'CertScanner', 'Corporate Technology']
        change.window() == WINDOW
        change.template() == template()
        change.epicKeys() == ['CERT-1', 'CERT-5']
        change.storyKeys() == ['CERT-2', 'CERT-6', 'CERT-3']
        change.shortDescription() == 'CertScanner release: Expiry alerts; Audit trail'
        change.description() == '''\
                Production release of CertScanner (CERTSCANNER) in Corporate Technology, 2026-10-10 06:00 to 10:00 UTC.

                Change tasks, one per service: gui, backend-api.

                Scope from Jira project CERT:
                CERT-1 Expiry alerts (Done)
                - CERT-2 E-mail the owner (Done)
                - CERT-3 Teams alert (Done)
                CERT-5 Audit trail (Done)
                - CERT-6 Record each change (Done)

                About CertScanner:
                Watches TLS certificates.'''.stripIndent()
        change.tasks()*.serviceName() == ['gui', 'backend-api']
        change.tasks()*.number() == [null, null]
        change.tasks()*.shortDescription() == ['Deploy gui of CertScanner to production',
                                                'Deploy backend-api of CertScanner to production']
        change.tasks()[0].description() == ('Deploy gui of CertScanner (Angular front end), 2026-10-10 06:00 to'
                + ' 10:00 UTC. Install it on the virtual machines with UrbanCode Deploy, then run its smoke tests and'
                + ' confirm the result in this task.')
        change.tasks()[1].description().contains('backend-api of CertScanner, 2026-10-10 06:00 to 10:00 UTC. Roll out'
                + ' its new image on OpenShift')
    }

    def "texts typed by the user replace the generated ones"() {
        when:
        def change = draft(product, null, product.services().take(1), template(description: null), WINDOW, epics, [],
                ' Mine ', ' My description ')

        then:
        change.shortDescription() == 'Mine'
        change.description() == 'My description'
        descriptionOf(product, null, product.services(), template(description: null), WINDOW, epics.take(1), []) == '''\
                Production release of CertScanner (CERTSCANNER), 2026-10-10 06:00 to 10:00 UTC.

                Change tasks, one per service: gui, backend-api.

                Scope from Jira project CERT:
                CERT-1 Expiry alerts (Done)'''.stripIndent()
    }

    def "long texts are cut to what ServiceNow takes"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Epic number $it with a long summary that goes on and on") }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'x' * 60, 'CERT-1') }

        when:
        def summary = shortDescriptionOf(product, many)
        def text = descriptionOf(product, 'Custody', product.services(), template(), WINDOW, many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        summary.endsWith('...')
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
        shortDescriptionOf(product, []) == 'CertScanner production release'
    }

    def "texts with accented Jira summaries still fit the bytes of their Oracle columns"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Épique numéro $it – résumé très détaillé " + 'é' * 20) }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'ż' * 60, 'CERT-1') }

        when:
        def summary = shortDescriptionOf(product, many)
        def text = descriptionOf(product, 'Custody', product.services(), template(), WINDOW, many, lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
    }

    def "a raised change takes its number, the numbers of its tasks in order and its link"() {
        given:
        def drafted = draft(product, null, product.services(), template(), WINDOW, epics, [], null, null)

        when:
        def raised = drafted.numbered('CHG0001', ['CTASK0001', 'CTASK0002'], 'https://snow/CHG0001')

        then:
        raised.number() == 'CHG0001'
        raised.url() == 'https://snow/CHG0001'
        raised.tasks()*.number() == ['CTASK0001', 'CTASK0002']
        raised.tasks()*.serviceName() == ['gui', 'backend-api']
        raised.shortDescription() == drafted.shortDescription()
        raised.description() == drafted.description()
    }
}

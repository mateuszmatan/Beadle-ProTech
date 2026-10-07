package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SECTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.descriptionOf
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.draft
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.shortDescriptionOf
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.deployment
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ProductionChangeSpec extends Specification {

    static final Planning PLANNING = new Planning('Tests passed on QC.', 'Deploy the services.',
            'Run the smoke tests.', 'Redeploy the previous release.', 'The owner confirms the first use.')

    def product = product(code: 'CERTSCANNER', services: [
            [id: 10, name: 'gui', description: 'Angular front end'],
            [id: 11, name: 'backend-api', deployment: deployment(target: OPENSHIFT, appName: 'api',
                    artifactName: 'api')]])
    def epics = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]
    def stories = [story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record each change', 'CERT-5'),
                   story('CERT-3', 'Teams alert', 'CERT-1')]

    def "a draft writes the short description, the description and a task per service from Jira"() {
        when:
        def change = draft(product, 'Corporate Technology', product.services(), FIX_VERSION, schedule(),
                template(planning: PLANNING), epics, stories, ' ', null)

        then:
        [change.id(), change.number(), change.url(), change.createdAt()] == [null] * 4
        [change.productId(), change.productCode(), change.productName(), change.departmentName()] ==
                [1L, 'CERTSCANNER', 'CertScanner', 'Corporate Technology']
        change.fixVersion() == FIX_VERSION
        change.schedule() == schedule()
        change.template() == template(planning: PLANNING, release: FIX_VERSION)
        change.epicKeys() == ['CERT-1', 'CERT-5']
        change.storyKeys() == ['CERT-2', 'CERT-6', 'CERT-3']
        change.shortDescription() == 'CertScanner CERT 4.2: Expiry alerts; Audit trail'
        change.description() == '''\
                Production release CERT 4.2 of CertScanner (CERTSCANNER) in Corporate Technology.
                Installation 2026-10-10 06:00 to 10:00 UTC, post-install validation 2026-10-10 10:00 to 11:00 UTC, first usage 2026-10-12 08:00 UTC. No downtime.

                Change tasks, one per service: gui, backend-api.

                Scope from Jira project CERT, FixVersion CERT 4.2:
                CERT-1 Expiry alerts (Done)
                - CERT-2 E-mail the owner (Done)
                - CERT-3 Teams alert (Done)
                CERT-5 Audit trail (Done)
                - CERT-6 Record each change (Done)

                Test summary:
                Tests passed on QC.

                Implementation plan:
                Deploy the services.

                Validation plan:
                Run the smoke tests.

                Backout plan:
                Redeploy the previous release.

                First use plan:
                The owner confirms the first use.

                Privileged access: not needed.

                Risk assessment:
                BBH workgroups impacted: 1
                BBH users impacted: 10
                BBH applications impacted: 1
                Impacted clients: 0
                Impacted clients outside BBH: 0
                Business impact: Low
                Complexity of change: Low
                Complexity of validation: Low
                Backout testing and duration: Tested on QC, about 15 minutes
                Platform status: Existing platform

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

    def "the description names the downtime, the privileged users and a missing risk assessment"() {
        when:
        def text = descriptionOf(product, null, product.services(), 'R1', schedule(), template(description: null,
                downtime: true, privilegedAccess: privileged(2), riskAssessment: RiskAssessment.NONE), epics.take(1),
                [])

        then:
        text.startsWith('Production release R1 of CertScanner (CERTSCANNER).\n')
        text.contains('first usage 2026-10-12 08:00 UTC. Downtime expected during the installation.\n')
        text.contains('Scope from Jira project CERT, FixVersion R1:\nCERT-1 Expiry alerts (Done)\n\nTest summary:')
        text.contains('\nPrivileged access needed for: User 1 (adm_user1), User 2 (adm_user2).\n')
        text.endsWith('Risk assessment:\nNot assessed.')
    }

    def "texts typed by the user replace the generated ones and a given release is kept"() {
        when:
        def change = draft(product, null, product.services().take(1), FIX_VERSION, schedule(),
                template(release: 'Release 42'), epics, [], ' Mine ', ' My description ')

        then:
        change.shortDescription() == 'Mine'
        change.description() == 'My description'
        change.template().release() == 'Release 42'
        shortDescriptionOf(product, FIX_VERSION, []) == 'CertScanner CERT 4.2 production release'
    }

    def "long texts are cut to what ServiceNow takes"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Epic number $it with a long summary that goes on and on") }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'x' * 60, 'CERT-1') }
        def wordy = template(planning: new Planning(*(['p' * 2000] * 5)), description: 'd' * 2000,
                riskAssessment: new RiskAssessment(1, 2, 3, 4, 5, 'b' * 100, 'c' * 100, 'v' * 100, 'o' * 2000, 's' * 100))

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(product, 'Custody', product.services(), FIX_VERSION, schedule(), template(), many,
                lots)
        def longest = descriptionOf(product, 'Custody', product.services(), FIX_VERSION, schedule(), wordy, many,
                lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        summary.endsWith('...')
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
        bytes(longest) <= DESCRIPTION_MAX
        longest.contains('Test summary:\n' + 'p' * (SECTION_MAX - 3) + '...\n')
        longest.contains('Backout testing and duration: ooo')
    }

    def "texts with accented Jira summaries still fit the bytes of their Oracle columns"() {
        given:
        def many = (1..60).collect { epic("CERT-$it", "Épique numéro $it – résumé très détaillé " + 'é' * 20) }
        def lots = (1..80).collect { story("CERT-${100 + it}", "Story $it " + 'ż' * 60, 'CERT-1') }

        when:
        def summary = shortDescriptionOf(product, FIX_VERSION, many)
        def text = descriptionOf(product, 'Custody', product.services(), FIX_VERSION, schedule(), template(), many,
                lots)

        then:
        bytes(summary) <= SHORT_DESCRIPTION_MAX
        bytes(text) <= DESCRIPTION_MAX
        text.contains('more issues in Jira.')
        text.endsWith('About CertScanner:\nWatches TLS certificates.')
    }

    def "a raised change takes its number, the numbers of its tasks in order and its link"() {
        given:
        def drafted = draft(product, null, product.services(), FIX_VERSION, schedule(), template(), epics, [], null,
                null)

        when:
        def raised = drafted.numbered('CHG0001', ['CTASK0001', 'CTASK0002'], 'https://snow/CHG0001')

        then:
        raised.number() == 'CHG0001'
        raised.url() == 'https://snow/CHG0001'
        raised.tasks()*.number() == ['CTASK0001', 'CTASK0002']
        raised.tasks()*.serviceName() == ['gui', 'backend-api']
        [raised.fixVersion(), raised.schedule(), raised.template()] ==
                [drafted.fixVersion(), drafted.schedule(), drafted.template()]
        raised.shortDescription() == drafted.shortDescription()
        raised.description() == drafted.description()
    }
}

package com.bbh.itss.beadle.protech.support

import com.bbh.itss.beadle.protech.domain.change.ApprovalRole
import com.bbh.itss.beadle.protech.domain.change.ChangeApproval
import com.bbh.itss.beadle.protech.domain.change.ChangeSchedule
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Planning
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Timing
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.RiskAssessment
import com.bbh.itss.beadle.protech.domain.change.SecureCoding
import com.bbh.itss.beadle.protech.domain.change.TaskDetails
import com.bbh.itss.beadle.protech.domain.change.TaskState
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep

import java.time.Instant

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Type.STANDARD
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN

class ChangeFixtures {

    static final String FIX_VERSION = 'CERT 4.2'
    static final Instant RAISED = Instant.parse('2026-10-05T09:00:00Z')

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }

    static ChangeTemplate template(Map changes = [:]) {
        copy(changes, ChangeTemplate.builder().jiraProjectKey('CERT').assignmentGroup('Technology Architecture')
                .category('Application').type(STANDARD).configurationItem('CertScanner')
                .approvers(new Approvers('Olivia Bennett', 'James Carter', 'Rebecca Lawson', 'Jane Smith'))
                .timing(new Timing('18:00', 2, 1)).planning(new Planning('Pipeline tests passed on QC.',
                'Deploy the services.', 'Run the smoke tests.', 'Redeploy the previous release.',
                'The business owner confirms the first use.')).riskAssessment(risk()).build())
    }

    static SecureCoding secureCoding(Map changes = [:]) {
        copy(changes, new SecureCoding('APO-12345', 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                'https://jenkins.bbh.com/job/CERT/job/cert-release/', 'https://cert.qc.bbh.com'))
    }

    static RiskAssessment risk(Map changes = [:]) {
        copy(changes, new RiskAssessment('Single', 'Simple', '5-25', 'Simple', 'Single', 'Less than 30 minutes',
                'No clients', 'Existing', 'Low'))
    }

    static PrivilegedAccess privileged(int users = 2) {
        new PrivilegedAccess(true, (1..users).collect { new PrivilegedUser("User $it", "adm_user$it") })
    }

    static ChangeSchedule schedule(Map changes = [:]) {
        copy(changes.collectEntries { key, value -> [key, value instanceof String ? at(value) : value] },
                new ChangeSchedule(at('2026-10-10T06:00:00Z'), at('2026-10-10T10:00:00Z'),
                        at('2026-10-10T10:00:00Z'), at('2026-10-10T11:00:00Z'), at('2026-10-12T08:00:00Z'), null,
                        null))
    }

    static TaskDetails details(int index = 1, Map changes = [:]) {
        copy(changes, TaskDetails.builder().assignmentGroup('Technology Architecture')
                .shortDescription("Task $index of the CertScanner release")
                .description("Step $index of the CertScanner release.").build())
    }

    static List<TaskDetails> tasks(int count = 2) {
        (1..count).collect { details(it) }
    }

    static ChangeTask task(int index = 1) {
        ctask("CTASK004100$index", "Task $index of the CertScanner release", "Step $index of the CertScanner release.",
                OPEN)
    }

    static ChangeTask ctask(String number, String shortDescription, String description, TaskState state) {
        new ChangeTask(number, TaskDetails.builder().assignmentGroup('Technology Architecture')
                .configurationItem('CertScanner').shortDescription(shortDescription).description(description).build(),
                null, null, null, null, state)
    }

    static ChangeTask releaseTask(Map changes = [:], String start = '2026-10-10T06:01:00Z') {
        new ChangeTask(null, details(3, [assignmentGroup: 'Release Management', application: 'CertScanner'] + changes),
                start == null ? null : at(start), null, null, null, OPEN)
    }

    static ProductionChange raised(Map changes = [:]) {
        copy(changes, ProductionChange.builder().id(7L).number('CHG0031001').productId(1L).productCode('CERTSCANNER')
                .productName('CertScanner').departmentId(3L).departmentName('Corporate Technology')
                .fixVersion(FIX_VERSION).schedule(schedule()).shortDescription('CertScanner CERT 4.2: Expiry alerts')
                .description('Production release CERT 4.2 of CertScanner.').template(template(release: FIX_VERSION))
                .epicKeys(['CERT-1']).storyKeys(['CERT-2'])
                .tasks([task(1), task(2)])
                .state(DRAFT).workflow([new WorkflowStep(DRAFT, RAISED)])
                .approvals(ApprovalRole.values().collect { new ChangeApproval(it, it.approverIn(template().approvers()),
                        null, null) }).syncedAt(RAISED).version(0L)
                .createdAt(RAISED).build())
    }

    static Instant at(String text) {
        Instant.parse(text)
    }
}

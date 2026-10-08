package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.JiraIssue
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.change.WorkflowStep

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.support.Fixtures.copy

class ChangeFixtures {

    static final String FIX_VERSION = 'CERT 4.2'
    static final Instant RAISED = Instant.parse('2026-10-05T09:00:00Z')

    static ChangeTemplate template(Map changes = [:]) {
        copy(changes, ChangeTemplate.builder().jiraProjectKey('CERT').assignmentGroup('Technology Architecture')
                .category('Software').type(NORMAL).configurationItem('CertScanner')
                .description('Watches TLS certificates.')
                .approvers(new Approvers('Olivia Bennett', 'James Carter', 'Rebecca Lawson'))
                .timing(Timing.SUGGESTED).planning(Planning.SUGGESTED).riskAssessment(risk()).build())
    }

    static RiskAssessment risk(Map changes = [:]) {
        copy(changes, new RiskAssessment(1, 10, 1, 0, 0, 'Low', 'Low', 'Low', 'Tested on QC, about 15 minutes',
                'Existing platform'))
    }

    static PrivilegedAccess privileged(int users = 2) {
        new PrivilegedAccess(true, (1..users).collect { new PrivilegedUser("User $it", "adm_user$it") })
    }

    static ChangeSchedule schedule(Map changes = [:]) {
        copy(changes.collectEntries { key, value -> [key, value instanceof String ? at(value) : value] },
                new ChangeSchedule(at('2026-10-10T06:00:00Z'), at('2026-10-10T10:00:00Z'),
                        at('2026-10-10T10:00:00Z'), at('2026-10-10T11:00:00Z'), at('2026-10-12T08:00:00Z')))
    }

    static List<TaskText> tasks(int count = 2) {
        (1..count).collect { new TaskText("Task $it of the CertScanner release", "Step $it of the CertScanner release.") }
    }

    static List<Map> tasksJson(int count = 2) {
        tasks(count).collect { [shortDescription: it.shortDescription(), description: it.description()] }
    }

    static ProductionChange raised(Map changes = [:]) {
        copy(changes, ProductionChange.builder().id(7L).number('CHG0031001').productId(1L).productCode('CERTSCANNER')
                .productName('CertScanner').departmentId(3L).departmentName('Corporate Technology')
                .fixVersion(FIX_VERSION).schedule(schedule()).shortDescription('CertScanner CERT 4.2: Expiry alerts')
                .description('Production release CERT 4.2 of CertScanner.').template(template(release: FIX_VERSION))
                .epicKeys(['CERT-1']).storyKeys(['CERT-2'])
                .tasks([new ChangeTask('CTASK0041001', 'Task 1 of the CertScanner release',
                        'Step 1 of the CertScanner release.', OPEN),
                        new ChangeTask('CTASK0041002', 'Task 2 of the CertScanner release',
                                'Step 2 of the CertScanner release.', OPEN)])
                .state(DRAFT).workflow([new WorkflowStep(DRAFT, RAISED)]).syncedAt(RAISED).version(0L)
                .createdAt(RAISED).build())
    }

    static JiraIssue epic(String key, String summary, String updated = '2026-09-20') {
        new JiraIssue(key, summary, 'Done', null, LocalDate.parse(updated))
    }

    static JiraIssue story(String key, String summary, String epicKey, String updated = '2026-09-18') {
        new JiraIssue(key, summary, 'Done', epicKey, LocalDate.parse(updated))
    }

    static Map templateJson(Map changes = [:]) {
        Map json = [jiraProjectKey   : 'CERT', assignmentGroup: 'Technology Architecture', category: 'Software',
                    type             : 'NORMAL', configurationItem: 'CertScanner', release: null, incident: null,
                    problem          : null, affectedClients: null, description: 'Watches TLS certificates.',
                    approvers        : [l1Manager: 'Olivia Bennett', l2Manager: 'James Carter',
                                        businessApprover: 'Rebecca Lawson'],
                    downtime         : false,
                    timing           : [installationStart: '18:00', installationHours: 2, validationHours: 1],
                    planning         : [testSummary       : 'Pipeline tests passed on QC.',
                                        implementationPlan: 'Deploy the services.',
                                        validationPlan    : 'Run the smoke tests.',
                                        backoutPlan       : 'Redeploy the previous release.',
                                        firstUsePlan      : 'The business owner confirms the first use.'],
                    privilegedAccess : [required: false, users: []],
                    riskAssessment   : [bbhWorkgroups: 1, bbhUsers: 10, bbhApplications: 1, clients: 0,
                                        clientsOutsideBbh: 0, businessImpact: 'Low', changeComplexity: 'Low',
                                        validationComplexity: 'Low', backoutTesting: 'Tested on QC, about 15 minutes',
                                        platformStatus: 'Existing platform']]
        changes.each { String path, value ->
            List<String> keys = path.tokenize('.')
            Map target = keys.init().inject(json) { Map map, String key -> map[key] as Map }
            target[keys.last()] = value
        }
        json
    }

    static Map scheduleJson(Instant installationStart) {
        [installationStart: installationStart.toString(), installationEnd: installationStart.plusSeconds(7200).toString(),
         validationStart  : installationStart.plusSeconds(7200).toString(),
         validationEnd    : installationStart.plusSeconds(10800).toString(),
         firstUsage       : installationStart.plusSeconds(54000).toString()]
    }

    static Instant at(String text) {
        Instant.parse(text)
    }
}

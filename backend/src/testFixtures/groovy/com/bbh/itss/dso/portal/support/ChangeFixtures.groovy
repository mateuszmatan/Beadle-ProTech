package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.change.ChangeProduct
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.JiraIssue
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.SecureCoding
import com.bbh.itss.dso.portal.domain.change.TaskDetails
import com.bbh.itss.dso.portal.domain.change.TaskState
import com.bbh.itss.dso.portal.domain.change.WorkflowStep

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.STANDARD
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.support.Fixtures.copy

class ChangeFixtures {

    static final String FIX_VERSION = 'CERT 4.2'
    static final Instant RAISED = Instant.parse('2026-10-05T09:00:00Z')

    static ChangeProduct changeProduct(Map changes = [:]) {
        copy(changes, new ChangeProduct(1L, 'CERTSCANNER', 'CertScanner', 'Technology Architecture', 3L,
                'Corporate Technology', null))
    }

    static ChangeTemplate template(Map changes = [:]) {
        copy(changes, ChangeTemplate.builder().jiraProjectKey('CERT').assignmentGroup('Technology Architecture')
                .category('Application').type(STANDARD).configurationItem('CertScanner')
                .approvers(new Approvers('Olivia Bennett', 'James Carter', 'Rebecca Lawson'))
                .timing(Timing.SUGGESTED).planning(Planning.SUGGESTED).riskAssessment(risk()).build())
    }

    static SecureCoding secureCoding(Map changes = [:]) {
        copy(changes, new SecureCoding('APO-12345', 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                'https://jenkins.bbh.com/job/CERT/job/cert-release/', 'https://cert.qc.bbh.com'))
    }

    static Map secureCodingJson(Map changes = [:]) {
        secureCodingJson(secureCoding(changes))
    }

    static Map secureCodingJson(SecureCoding inputs) {
        [apoNumber: inputs.apoNumber(), bitbucketUrl: inputs.bitbucketUrl(), artifactLink: inputs.artifactLink(),
         qcApplicationLink: inputs.qcApplicationLink()]
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

    static List<Map> tasksJson(int count = 2) {
        tasks(count).collect { [assignmentGroup: it.assignmentGroup(), shortDescription: it.shortDescription(),
                                description    : it.description()] }
    }

    static Map detailsJson(TaskDetails details) {
        [assignmentGroup   : details.assignmentGroup(), assignedTo: details.assignedTo(),
         configurationItem : details.configurationItem(), platform: details.platform(),
         application       : details.application(), packages: details.packages(),
         backoutPackages   : details.backoutPackages(), importance: details.importance(),
         shortDescription  : details.shortDescription(), description: details.description(),
         additionalComments: details.additionalComments()]
    }

    static Map changeTaskJson(Map details = [:], Instant start = null) {
        [details: tasksJson(1)[0] + details, start: start?.toString()]
    }

    static List<Map> changeTasksJson(int count = 2) {
        tasksJson(count).collect { [details: it, start: null] }
    }

    static List<TaskDetails> migratedTasks(String productName) {
        TaskDetails.suggestedTasks(productName, null).collect {
            it.toBuilder().assignmentGroup('Technology Architecture').configurationItem('CertScanner').build()
        }
    }

    static ChangeTask task(int index = 1) {
        ctask("CTASK004100$index", "Task $index of the CertScanner release", "Step $index of the CertScanner release.",
                OPEN)
    }

    static ChangeTask ctask(String number, String shortDescription, String description, TaskState state) {
        new ChangeTask(number, TaskDetails.builder().assignmentGroup('Technology Architecture')
                .configurationItem('CertScanner').shortDescription(shortDescription).description(description).build(),
                null, null, state)
    }

    static ChangeTask releaseTask(Map changes = [:], String start = '2026-10-10T06:01:00Z') {
        new ChangeTask(null, details(3, [assignmentGroup: 'Release Management', application: 'CertScanner'] + changes),
                start == null ? null : at(start), null, OPEN)
    }

    static ProductionChange raised(Map changes = [:]) {
        copy(changes, ProductionChange.builder().id(7L).number('CHG0031001').productId(1L).productCode('CERTSCANNER')
                .productName('CertScanner').departmentId(3L).departmentName('Corporate Technology')
                .fixVersion(FIX_VERSION).schedule(schedule()).shortDescription('CertScanner CERT 4.2: Expiry alerts')
                .description('Production release CERT 4.2 of CertScanner.').template(template(release: FIX_VERSION))
                .epicKeys(['CERT-1']).storyKeys(['CERT-2'])
                .tasks([task(1), task(2)])
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
        Map json = [jiraProjectKey    : 'CERT', requestedFor: null, requestedBy: null, department: null,
                    assignmentGroup   : 'Technology Architecture', category: 'Application', assignedTo: null,
                    type              : 'STANDARD', release: null, configurationItem: 'CertScanner',
                    incident          : null, directBusinessService: null, problem: null, risk: 'Moderate',
                    affectedClients   : null, usersAffected: null,
                    approvers         : [l1Manager: 'Olivia Bennett', l2Manager: 'James Carter',
                                         businessApprover: 'Rebecca Lawson'],
                    downtime          : false,
                    timing            : [installationStart: '18:00', installationHours: 2, validationHours: 1],
                    planning          : [testSummary       : 'Pipeline tests passed on QC.',
                                         implementationPlan: 'Deploy the services.',
                                         validationPlan    : 'Run the smoke tests.',
                                         backoutPlan       : 'Redeploy the previous release.',
                                         firstUsePlan      : 'The business owner confirms the first use.'],
                    privilegedAccess  : [required: false, users: []],
                    riskAssessment    : [bbhWorkgroups: 'Single', changeComplexity: 'Simple', bbhUsers: '5-25',
                                         validationComplexity: 'Simple', bbhApplications: 'Single',
                                         backoutTesting: 'Less than 30 minutes', clientsOutsideBbh: 'No clients',
                                         platformStatus: 'Existing', businessImpact: 'Low'],
                    secureCodingTicket: null,
                    secureCoding      : secureCodingJson(SecureCoding.NONE)]
        changes.each { String path, value ->
            List<String> keys = path.tokenize('.')
            Map target = keys.init().inject(json) { Map map, String key -> map[key] as Map }
            target[keys.last()] = value
        }
        json
    }

    static Map scheduleJson(Instant installationStart, boolean downtime = false) {
        [installationStart: installationStart.toString(), installationEnd: installationStart.plusSeconds(7200).toString(),
         validationStart  : installationStart.plusSeconds(7200).toString(),
         validationEnd    : installationStart.plusSeconds(10800).toString(),
         firstUsage       : installationStart.plusSeconds(54000).toString(),
         downtimeStart    : downtime ? installationStart.toString() : null,
         downtimeEnd      : downtime ? installationStart.plusSeconds(5400).toString() : null]
    }

    static Instant at(String text) {
        Instant.parse(text)
    }
}

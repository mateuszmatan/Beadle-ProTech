package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.JiraIssue

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL
import static com.bbh.itss.dso.portal.support.Fixtures.copy

class ChangeFixtures {

    static final String FIX_VERSION = 'CERT 4.2'

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

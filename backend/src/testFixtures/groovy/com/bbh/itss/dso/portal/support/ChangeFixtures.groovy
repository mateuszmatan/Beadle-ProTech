package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.JiraIssue

import java.time.LocalDate

class ChangeFixtures {

    static ChangeTemplate template(Map changes = [:]) {
        Fixtures.copy(changes, new ChangeTemplate('CERT', 'CertScanner', 'Technology Architecture',
                ChangeTemplate.Type.NORMAL, 'Software', ChangeTemplate.Risk.MODERATE, ChangeTemplate.Impact.LOW,
                'Tested on QC.', ['Olivia Bennett', 'James Carter'], 'Watches TLS certificates.',
                ChangeTemplate.IMPLEMENTATION_PLAN, ChangeTemplate.BACKOUT_PLAN, ChangeTemplate.TEST_PLAN))
    }

    static JiraIssue epic(String key, String summary, String updated = '2026-09-20') {
        new JiraIssue(key, summary, 'Done', null, LocalDate.parse(updated))
    }

    static JiraIssue story(String key, String summary, String epicKey, String updated = '2026-09-18') {
        new JiraIssue(key, summary, 'Done', epicKey, LocalDate.parse(updated))
    }

    static Map templateJson(Map changes = [:]) {
        [jiraProjectKey    : 'CERT', configurationItem: 'CertScanner', assignmentGroup: 'Technology Architecture',
         type              : 'NORMAL', category: 'Software', risk: 'MODERATE', impact: 'LOW',
         riskAssessment    : 'Tested on QC.', approvers: ['Olivia Bennett', 'James Carter'],
         description       : 'Watches TLS certificates.', implementationPlan: 'Deploy the services.',
         backoutPlan       : 'Redeploy the previous release.', testPlan: 'Pipeline tests passed on QC.'] + changes
    }
}

package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.JiraIssue

import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.BACKOUT_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.IMPLEMENTATION_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Impact.LOW
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Risk.MODERATE
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEST_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL
import static com.bbh.itss.dso.portal.support.Fixtures.copy

class ChangeFixtures {

    static ChangeTemplate template(Map changes = [:]) {
        copy(changes, new ChangeTemplate('CERT', 'CertScanner', 'Technology Architecture', NORMAL, 'Software',
                MODERATE, LOW, 'Tested on QC.', ['Olivia Bennett', 'James Carter'], 'Watches TLS certificates.',
                IMPLEMENTATION_PLAN, BACKOUT_PLAN, TEST_PLAN))
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

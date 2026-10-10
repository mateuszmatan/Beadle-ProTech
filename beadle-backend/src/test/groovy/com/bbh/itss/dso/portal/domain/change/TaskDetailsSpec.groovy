package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.suggestedTasks
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.validateTasks
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes
import static com.bbh.itss.dso.portal.support.ChangeFixtures.at
import static com.bbh.itss.dso.portal.support.ChangeFixtures.ctask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.details
import static com.bbh.itss.dso.portal.support.ChangeFixtures.releaseTask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule

class TaskDetailsSpec extends Specification {

    def "a task of any other group trims its texts, is moderate by default and keeps no release fields"() {
        when:
        def task = new TaskDetails(' Cloud Engineering ', ' Anna Nowak ', ' CertScanner ', 'OpenShift', 'App',
                'pkg', 'old', null, ' Open the firewall ', ' Open it. ', ' ')

        then:
        task == TaskDetails.builder().assignmentGroup('Cloud Engineering').assignedTo('Anna Nowak')
                .configurationItem('CertScanner').importance('3 - Moderate').shortDescription('Open the firewall')
                .description('Open it.').build()
        !task.releaseManagement()
        details(1, [importance: '1 - Critical']).importance() == '1 - Critical'
    }

    def "a Release Management task runs on no platform by default, has no importance and OpenShift runs as OCP"() {
        when:
        def release = details(1, [assignmentGroup: ' corporate RELEASE management ', importance: '2 - High',
                                  application    : ' CertScanner ', packages: ' cert-4.2.tar ',
                                  backoutPackages: ' cert-4.1.tar '])
        def openShift = details(1, [assignmentGroup: 'Release Management', platform: 'OpenShift',
                                    application    : 'CertScanner'])

        then:
        release.releaseManagement()
        [release.platform(), release.importance(), release.application(), release.packages(),
         release.backoutPackages()] == ['None', null, 'CertScanner', 'cert-4.2.tar', 'cert-4.1.tar']
        openShift.application() == 'OCP'
    }

    def "a change task is open and not approved by default and only a release task keeps a start"() {
        expect:
        ctask(' CTASK1 ', ' Deploy ', ' It. ', null) == ctask('CTASK1', 'Deploy', 'It.', OPEN)
        ChangeTask.of(details()) == new ChangeTask(null, details(), null, NOT_APPROVED, [], null, OPEN)
        new ChangeTask(null, details(), at('2026-10-10T06:30:00Z'), null, null, null, null).start() == null
        releaseTask().start() == at('2026-10-10T06:01:00Z')
        new ChangeTask(null, null, null, null, null, null, null).details() == TaskDetails.builder().build()
        [OPEN, WORK_IN_PROGRESS, CLOSED, CANCELED].collect { it.frozen() } == [false, false, true, true]
    }

    def "a planned task falls back to the affected CI of the change and starts a minute into the installation"() {
        when:
        def planned = releaseTask([configurationItem: null], null).plannedIn(schedule(), 'CertScanner')
        def own = releaseTask([configurationItem: 'CertScanner UI'], '2026-10-10T07:00:00Z')
                .plannedIn(schedule(), 'CertScanner')

        then:
        planned.details().configurationItem() == 'CertScanner'
        planned.start() == at('2026-10-10T06:01:00Z')
        own.details().configurationItem() == 'CertScanner UI'
        own.start() == at('2026-10-10T07:00:00Z')
        releaseTask().plannedIn(ChangeSchedule.UNPLANNED, 'CertScanner').start() == at('2026-10-10T06:01:00Z')
    }

    def "a product without a template deploys through Release Management and validates in its support group"() {
        when:
        def suggested = suggestedTasks('CertScanner', 'Technology Architecture')
        def cut = suggestedTasks('é' * 150, 'Technology Architecture')

        then:
        suggested == [TaskDetails.builder().assignmentGroup('Release Management').application('CertScanner')
                              .shortDescription('Deploy CertScanner to production')
                              .description('Deploy the release of CertScanner in the change window with its deployment'
                                      + ' jobs, then run the smoke tests of the DevSecOps pipeline and record the result'
                                      + ' in this task.').build(),
                      TaskDetails.builder().assignmentGroup('Technology Architecture')
                              .shortDescription('Validate CertScanner in production')
                              .description('Run the post-install validation of CertScanner: the smoke tests and the'
                                      + ' monitoring, then confirm the release with the business owner in this task.')
                              .build()]
        suggested[0].platform() == 'None'
        suggested[1].importance() == '3 - Moderate'
        cut.every { bytes(it.shortDescription()) <= 160 && it.shortDescription().endsWith('...') }
        bytes(cut[0].application()) <= 100
    }

    def "the template tasks are refused when #problem"() {
        given:
        def problems = new ValidationProblems()

        when:
        validateTasks(tasks, problems)

        then:
        problems.list().collect { it.field() + ': ' + it.message() } == expected

        where:
        problem                     | tasks                                                                    || expected
        'there are none'            | []                                                                       || ['tasks: add at least one change task']
        'there are 51'              | (1..51).collect { details(it) }                                          || ['tasks: may list at most 50 change tasks']
        'a field is missing'        | [details(1), new TaskDetails(null, null, null, null, null, null, null, null, ' ', null, null)] || ['tasks[1].assignmentGroup: is required', 'tasks[1].shortDescription: is required', 'tasks[1].description: is required']
        'a text is too long'        | [details(1, [shortDescription: 'é' * 81, description: 'x' * 4001])]       || ['tasks[0].shortDescription: is too long: it may take at most 160 bytes', 'tasks[0].description: is too long: it may take at most 4000 bytes']
        'a lookup is too long'      | [details(1, [assignedTo: 'x' * 201, configurationItem: 'x' * 201])]      || ['tasks[0].assignedTo: is too long: it may take at most 200 bytes', 'tasks[0].configurationItem: is too long: it may take at most 200 bytes']
        'a package list is too long' | [releaseTask([packages: 'x' * 2001, backoutPackages: 'x' * 2001, application: 'x' * 101, additionalComments: 'x' * 2001]).details()] || ['tasks[0].application: is too long: it may take at most 100 bytes', 'tasks[0].packages: is too long: it may take at most 2000 bytes', 'tasks[0].backoutPackages: is too long: it may take at most 2000 bytes', 'tasks[0].additionalComments: is too long: it may take at most 2000 bytes']
        'the importance is unknown' | [details(1, [importance: 'Urgent'])]                                     || ['tasks[0].importance: must be one of 1 - Critical, 2 - High, 3 - Moderate, 4 - Low, 5 - Planning']
        'the platform is unknown'   | [releaseTask([platform: 'Windows']).details()]                           || ['tasks[0].platform: must be one of None, Mainframe, Distributed, OpenShift, Cognos/Motio']
        'nothing, they fit'         | [details(1, [shortDescription: 'é' * 80, description: 'x' * 4000]), releaseTask().details()] || []
    }

    def "the change tasks are refused when #problem"() {
        given:
        def problems = new ValidationProblems()

        when:
        ChangeTask.validateTasks(tasks, schedule(), problems)

        then:
        problems.list().collect { it.field() + ': ' + it.message() } == expected

        where:
        problem                           | tasks                                                      || expected
        'none are listed, which is fine'  | []                                                         || []
        'a release task has no start'     | [releaseTask([:], null)]                                   || ['tasks[0].start: choose when the task starts']
        'it starts with the installation' | [releaseTask([:], '2026-10-10T06:00:59Z')]                 || ['tasks[0].start: must be at least a minute after the installation start']
        'it starts after the installation' | [releaseTask([:], '2026-10-10T10:00:01Z')]                || ['tasks[0].start: must not be after the installation end']
        'its details are refused'         | [ChangeTask.of(details(1, [description: null]))]           || ['tasks[0].details.description: is required']
        'nothing, they fit'               | [ChangeTask.of(details()), releaseTask([:], '2026-10-10T10:00:00Z')] || []
    }
}

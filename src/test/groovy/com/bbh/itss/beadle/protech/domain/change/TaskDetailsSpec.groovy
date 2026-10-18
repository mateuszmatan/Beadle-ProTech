package com.bbh.itss.beadle.protech.domain.change

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.validateTasks
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN
import static com.bbh.itss.beadle.protech.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.at
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.ctask
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.details
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.schedule

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
        new ChangeTask(null, details(), null, null, null, null, null) == new ChangeTask(null, details(), null, NOT_APPROVED, [], null, OPEN)
        new ChangeTask(null, details(), at('2026-10-10T06:30:00Z'), null, null, null, null).start() == null
        releaseTask().start() == at('2026-10-10T06:01:00Z')
        new ChangeTask(null, null, null, null, null, null, null).details() == TaskDetails.builder().build()
        [OPEN, WORK_IN_PROGRESS, CLOSED, CANCELED].collect { it.frozen() } == [false, false, true, true]
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
        'its details are refused'         | [new ChangeTask(null, details(1, [description: null]), null, null, null, null, null)]           || ['tasks[0].details.description: is required']
        'nothing, they fit'               | [new ChangeTask(null, details(), null, null, null, null, null), releaseTask([:], '2026-10-10T10:00:00Z')] || []
    }
}

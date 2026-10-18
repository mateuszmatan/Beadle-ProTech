package com.bbh.itss.beadle.protech.domain.change

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.DESCRIPTION_MAX
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX
import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.MAX_TASKS
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.details
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.issued
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.schedule
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.template

class ProductionChangeSpec extends Specification {

    def "a change Beadle-GUI sends without lists, state or edited version has empty lists, is a draft and edits its version"() {
        when:
        def change = ProductionChange.builder().number('CHG0031001').version(4L).build()

        then:
        [change.epicKeys(), change.storyKeys(), change.tasks(), change.workflow(), change.approvals()] == [[]] * 5
        change.state() == DRAFT
        change.editedVersion() == 4L
        change.toBuilder().editedVersion(3L).build().editedVersion() == 3L
    }

    def "a created change takes its number and link, and created tasks are added after the ones it has"() {
        given:
        def change = issued(number: null, url: null, tasks: [task(1)])

        expect:
        change.numbered('CHG0031009', 'https://protech/CHG0031009').with { [number(), url()] } ==
                ['CHG0031009', 'https://protech/CHG0031009']
        change.withTasks([task(2), task(3)]).tasks() == [task(1), task(2), task(3)]
    }

    def "a change is refused when #problem"() {
        given:
        def problems = new ValidationProblems()

        when:
        issued(edits).validate(problems)

        then:
        problems.list().collect { it.field() + ' ' + it.message() } == expected

        where:
        problem                          | edits                                                               || expected
        'nothing is wrong'               | [:]                                                                 || []
        'its texts are missing'          | [shortDescription: ' ', description: null]                          || ['shortDescription is required', 'description is required']
        'its texts are too long'         | [shortDescription: 'x' * (SHORT_DESCRIPTION_MAX + 1), description: 'ł' * (DESCRIPTION_MAX.intdiv(2) + 1)] || ["shortDescription is too long: it may take at most $SHORT_DESCRIPTION_MAX bytes", "description is too long: it may take at most $DESCRIPTION_MAX bytes"]*.toString()
        'it has no template or schedule' | [template: null, schedule: null]                                    || ['template fill in the ProTech fields of the change', 'schedule choose when the change is installed, validated and first used']
        'its template is incomplete'     | [template: template(category: null)]                                || ['template.category is required']
        'its downtime has no window'     | [template: template(downtime: true)]                                || ['schedule.downtimeStart choose when the downtime starts', 'schedule.downtimeEnd choose when the downtime ends']
        'a task starts too early'        | [tasks: [releaseTask([:], '2026-10-10T06:00:30Z')]]                 || ['tasks[0].start must be at least a minute after the installation start']
        'a task has no description'      | [tasks: [new ChangeTask(null, details(1, [description: ' ']), null, null, null, null, null)]] || ['tasks[0].details.description is required']
        'it has too many tasks'          | [tasks: [task(1)] * (MAX_TASKS + 1)]                                || ["tasks may list at most $MAX_TASKS change tasks".toString()]
        'its schedule is out of order'   | [schedule: schedule(installationEnd: '2026-10-10T05:00:00Z')]       || ['schedule.installationEnd must be after the installation start']
    }
}

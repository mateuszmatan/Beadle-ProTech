package com.bbh.itss.beadle.protech.application.change

import com.bbh.itss.beadle.protech.application.calllog.CallerContext
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort
import com.bbh.itss.beadle.protech.domain.calllog.Caller
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.ProTechStatus
import com.bbh.itss.beadle.protech.domain.change.RaisedChange
import com.bbh.itss.beadle.protech.domain.change.TrackedChange
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.protech.application.change.ChangeService.ONE_OF_ROLE_OR_TASK
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.L1
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.DEMO
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.LIVE
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.details
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.schedule
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.template
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class ChangeServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    ProTechPort proTech = Mock()
    TrackedChangePort tracked = Mock()
    def service = new ChangeService(proTech, tracked, fixed(NOW, UTC))

    def setup() {
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))
    }

    def cleanup() {
        CallerContext.clear()
    }

    def "the status says whether ProTech is reached and which ProTech it is"() {
        given:
        proTech.connected() >> connected
        proTech.mode() >> mode

        expect:
        service.status() == new ProTechStatus(connected, mode)

        where:
        connected | mode
        false     | DEMO
        true      | LIVE
    }

    def "a draft is raised in ProTech as it is, answers its number and its link and is followed from its draft"() {
        given:
        def draft = raised(number: null, tasks: [], workflow: [], approvals: [])

        when:
        def result = service.raise(draft)

        then:
        1 * proTech.raise(draft) >> new RaisedChange('CHG0031002', 'https://protech/CHG0031002')
        1 * tracked.track([TrackedChange.seen('CHG0031002', DRAFT, NOW, 'corr-1')])
        result == new RaisedChange('CHG0031002', 'https://protech/CHG0031002')
    }

    def "what ProTech refuses to do is no news to Beadle-GUI either"() {
        when:
        service.update('CHG0031001', raised())

        then:
        1 * proTech.update(_) >> { throw new IllegalStateException('ProTech does not change a closed change') }
        0 * tracked._
        thrown(IllegalStateException)
    }

    def "a change ProTech does not raise is not followed"() {
        when:
        service.raise(raised(number: null))

        then:
        1 * proTech.raise(_) >> { throw new UncheckedIOException('ProTech could not raise the change: 503', new IOException()) }
        0 * tracked._
        thrown(UncheckedIOException)
    }

    def "a change with #problem is refused with its fields and never reaches ProTech"() {
        when:
        service.raise(raised(changes))

        then:
        0 * proTech._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields

        when:
        service.update('CHG0031001', raised(changes))

        then:
        0 * proTech._
        thrown(InvalidRequestException)

        where:
        problem                          | changes                                                       || fields
        'no texts'                       | [shortDescription: null, description: ' ']                    || ['shortDescription', 'description']
        'a text too long'                | [shortDescription: 'x' * 161]                                 || ['shortDescription']
        'no template'                    | [template: null]                                              || ['template']
        'an incomplete template'         | [template: template(assignmentGroup: null)]                   || ['template.assignmentGroup']
        'no schedule'                    | [schedule: null]                                              || ['schedule']
        'a schedule out of order'        | [schedule: schedule(installationEnd: '2026-10-10T05:00:00Z')] || ['schedule.installationEnd']
        'a task without a group'         | [tasks: [new ChangeTask(null, details(1, [assignmentGroup: null]), null, null, null, null, null)]] || ['tasks[0].details.assignmentGroup']
        'a release task without a start' | [tasks: [releaseTask([:], null)]]                             || ['tasks[0].start']
    }

    def "a task is created on the change and answers its CTASK number"() {
        when:
        def number = service.createTask('CHG0031001', task(1))

        then:
        1 * proTech.createTask('CHG0031001', task(1)) >> 'CTASK0041003'
        1 * tracked.touch('CHG0031001', NOW, 'corr-1')
        number == 'CTASK0041003'
    }

    def "a task with #problem is not created"() {
        when:
        service.createTask(number, task)

        then:
        0 * proTech._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields

        where:
        problem                          | number       | task                                               || fields
        'no change number'               | ' '          | task(1)                                            || ['number']
        'no short description'           | 'CHG0031001' | new ChangeTask(null, details(1, [shortDescription: null]), null, null, null, null, null) || ['details.shortDescription']
        'a release task without a start' | 'CHG0031001' | releaseTask([:], null)                             || ['start']
    }

    def "the known changes are read from ProTech by their numbers"() {
        given:
        def known = [raised(), raised(number: 'CHG0031002')]

        when:
        def found = service.read(known)

        then:
        1 * proTech.read(known) >> [CHG0031001: known[0].toBuilder().state(PRIMARY_APPROVAL).build()]
        1 * tracked.track([TrackedChange.seen('CHG0031001', PRIMARY_APPROVAL, NOW, 'corr-1')])
        found == [CHG0031001: known[0].toBuilder().state(PRIMARY_APPROVAL).build()]
    }

    def "reading no change asks nothing of ProTech and follows nothing"() {
        when:
        def found = service.read(known)

        then:
        found == [:]
        0 * proTech._
        0 * tracked._

        where:
        known << [[], null]
    }

    def "a change without a number cannot be read"() {
        when:
        service.read([raised(), raised(number: null)])

        then:
        0 * proTech._
        0 * tracked._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['[1].number']
    }

    def "an update goes to ProTech under the number of the path, also when the body has none"() {
        when:
        service.update('CHG0031001', raised(number: number))

        then:
        1 * proTech.update(raised())
        1 * tracked.touch('CHG0031001', NOW, 'corr-1')

        where:
        number << ['CHG0031001', null]
    }

    def "an update of another change than the path's is refused"() {
        when:
        service.update('CHG0031002', raised())

        then:
        0 * proTech._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['number']
        refused.message == 'must be CHG0031002, the change of the path'
    }

    def "a reminder names the role of the change approver or the change task and answers whom ProTech reminded"() {
        when:
        def sent = service.remind('CHG0031001', approval)

        then:
        1 * proTech.remind('CHG0031001', expected) >> ['Olivia Bennett']
        1 * tracked.touch('CHG0031001', NOW, 'corr-1')
        sent == ['Olivia Bennett']

        where:
        approval                              || expected
        new ApprovalRef(L1, null)             || new ApprovalRef(L1, null)
        new ApprovalRef(null, ' CTASK0041001 ') || new ApprovalRef(null, 'CTASK0041001')
    }

    def "a reminder with #problem is refused"() {
        when:
        service.remind(number, approval)

        then:
        0 * proTech._
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == [field]
        refused.message == message

        where:
        problem                     | number       | approval                            || field    | message
        'neither a role nor a task' | 'CHG0031001' | new ApprovalRef(null, ' ')          || 'task'   | ONE_OF_ROLE_OR_TASK
        'both a role and a task'    | 'CHG0031001' | new ApprovalRef(L1, 'CTASK0041001') || 'task'   | ONE_OF_ROLE_OR_TASK
        'no change number'          | ''           | new ApprovalRef(L1, null)           || 'number' | 'is required'
    }
}

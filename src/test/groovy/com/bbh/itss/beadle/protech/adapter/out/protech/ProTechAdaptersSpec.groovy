package com.bbh.itss.beadle.protech.adapter.out.protech

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ApprovalField
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeField
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.LookupTable
import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.TaskField
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.protech.config.ClockConfiguration
import com.bbh.itss.beadle.protech.domain.change.ChangeState
import com.bbh.itss.beadle.protech.support.EnvironmentVariables
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Duration

import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.CLIENTS
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.RELEASES
import static com.bbh.itss.beadle.protech.domain.change.LookupKind.USERS
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.DEMO
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED

class ProTechAdaptersSpec extends Specification {

    def runner = new ApplicationContextRunner().withUserConfiguration(Adapters)

    def "the demo ProTech plays until beadle.protech.instance-url names the real one"() {
        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBeansOfType(ProTechPort).values()*.class == changes
            assert context.getBeansOfType(ProTechLookupPort).values()*.class == lookups
            true
        }

        where:
        properties                                                  || changes                     | lookups
        []                                                          || [DemoProTechAdapter]        | [DemoProTechLookups]
        ['beadle.protech.instance-url=']                            || [DemoProTechAdapter]        | [DemoProTechLookups]
        ['beadle.protech.instance-url=https://protech.bbh.test'] || [LiveProTechAdapter]    | [LiveProTechAdapter]
    }

    def "Beadle-GUI's BEADLE_PROTECH_URL among the environment variables leaves the demo ProTech playing"() {
        expect: 'Spring binds the variable to beadle.protech.url, which names nothing here'
        runner.withInitializer(new EnvironmentVariables()).run { context ->
            assert context.environment.getProperty('beadle.protech.url') == 'http://localhost:8083'
            assert context.getBean(ProTechProperties).instanceUrl() == null
            def proTech = context.getBean(ProTechPort)
            assert proTech instanceof DemoProTechAdapter
            assert !proTech.connected()
            assert proTech.mode() == DEMO
            assert context.getBean(ProTechLookupPort) instanceof DemoProTechLookups
            true
        }
    }

    def "the ProTech properties take the address, the account and the demo's apply delay, the fields default to ProTech's"() {
        expect:
        runner.withPropertyValues('beadle.protech.instance-url=https://protech.bbh.test', 'beadle.protech.username=beadle',
                'beadle.protech.password=secret', 'beadle.protech.demo.apply-delay=PT1S')
                .run { context ->
                    assert context.getBean(ProTechProperties) == new ProTechProperties('https://protech.bbh.test',
                            'beadle', 'secret', ProTechFields.DEFAULTS)
                    assert context.getBean(DemoProTechProperties) == new DemoProTechProperties(Duration.ofSeconds(1))
                    true
                }
        runner.run { context ->
            assert context.getBean(ProTechProperties) == new ProTechProperties(null, null, null, ProTechFields.DEFAULTS)
            true
        }
    }

    def "beadle.protech.fields renames a field, leaves one out with an empty name and keeps the defaults of the rest"() {
        expect:
        runner.withPropertyValues('beadle.protech.fields.change.table=x_bbh_change',
                'beadle.protech.fields.change.department=',
                'beadle.protech.fields.change.release=u_bbh_release',
                'beadle.protech.fields.change.l1-manager=u_first_manager',
                'beadle.protech.fields.task.importance=u_importance',
                'beadle.protech.fields.approval.comments=work_notes',
                'beadle.protech.fields.change-states.implementation=-1,In Progress',
                'beadle.protech.fields.change-states.business-approval=10,Business Approval',
                'beadle.protech.fields.task-states.canceled=8,Cancelled',
                'beadle.protech.fields.approval-states.approved=approved',
                'beadle.protech.fields.lookups.releases.table=u_bbh_release',
                'beadle.protech.fields.lookups.clients.filter=')
                .run { context ->
                    def fields = context.getBean(ProTechProperties).fields()
                    assert fields.column(ChangeField.TABLE) == 'x_bbh_change'
                    assert fields.column(ChangeField.DEPARTMENT) == ''
                    assert fields.column(ChangeField.RELEASE) == 'u_bbh_release'
                    assert fields.column(ChangeField.L1_MANAGER) == 'u_first_manager'
                    assert fields.column(ChangeField.ASSIGNMENT_GROUP) == 'assignment_group'
                    assert fields.column(TaskField.IMPORTANCE) == 'u_importance'
                    assert fields.column(TaskField.TABLE) == 'change_task'
                    assert fields.column(ApprovalField.COMMENTS) == 'work_notes'
                    assert fields.column(ApprovalField.TABLE) == 'sysapproval_approver'
                    assert fields.changeStates()[IMPLEMENTATION] == ['-1', 'In Progress']
                    assert fields.changeStates()[BUSINESS_APPROVAL] == ['10', 'Business Approval']
                    assert fields.changeStates()[DRAFT] == ['-5', 'New', 'Draft']
                    assert fields.changeStates().keySet() as List == ChangeState.values() as List
                    assert fields.taskStates()[CANCELED] == ['8', 'Cancelled']
                    assert fields.approvalStates()[APPROVED] == ['approved']
                    assert fields.approvalStates()[REQUESTED] == ['requested']
                    assert fields.lookups()[RELEASES] == new LookupTable('u_bbh_release', 'short_description', 'number', '')
                    assert fields.lookups()[CLIENTS] == new LookupTable('core_company', 'name', 'country', '')
                    assert fields.lookups()[USERS] == new LookupTable('sys_user', 'name', 'email', 'active=true')
                    true
                }
    }

    def "every state of a change has default values, with the value of ServiceNow's base system first"() {
        expect:
        ProTechFields.DEFAULTS.changeStates().keySet() as List == ChangeState.values() as List
        ProTechFields.DEFAULTS.changeStates()[CLOSED].first() == '3'
        ProTechFields.DEFAULTS.taskStates()[CANCELED].first() == '4'
        ProTechFields.DEFAULTS.lookups().size() == 8
    }

    @EnableConfigurationProperties([ProTechProperties, DemoProTechProperties])
    @Import([ClockConfiguration, DemoProTechAdapter, DemoProTechLookups, LiveProTechAdapter])
    static class Adapters {

        @Bean
        RestClient.Builder restClientBuilder() {
            RestClient.builder()
        }
    }
}

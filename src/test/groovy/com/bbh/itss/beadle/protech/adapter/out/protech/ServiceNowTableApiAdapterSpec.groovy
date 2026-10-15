package com.bbh.itss.beadle.protech.adapter.out.protech

import com.bbh.itss.beadle.protech.adapter.out.protech.ProTechFields.ChangeFields
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity
import com.bbh.itss.beadle.protech.domain.change.ChangeApproval
import com.bbh.itss.beadle.protech.domain.change.ChangeTask
import com.bbh.itss.beadle.protech.domain.change.Lookup
import com.bbh.itss.beadle.protech.domain.change.LookupKind
import com.bbh.itss.beadle.protech.domain.change.ProductionChange
import com.bbh.itss.beadle.protech.domain.change.RaisedChange
import com.bbh.itss.beadle.protech.domain.change.Reminder
import com.bbh.itss.beadle.protech.domain.change.WorkflowStep
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.protech.adapter.out.protech.ServiceNowTableApiAdapter.NO_NUMBER
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.L1
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.L2
import static com.bbh.itss.beadle.protech.domain.change.ApprovalRole.SUPPORT
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Type.EMERGENCY
import static com.bbh.itss.beadle.protech.domain.change.ProTechMode.LIVE
import static com.bbh.itss.beadle.protech.domain.change.TaskState.CANCELED
import static com.bbh.itss.beadle.protech.domain.change.TaskState.OPEN
import static com.bbh.itss.beadle.protech.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.RAISED
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.at
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.copy
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.privileged
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.schedule
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.task
import static com.bbh.itss.beadle.protech.support.ChangeFixtures.template
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.ZoneOffset.UTC
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.PATCH
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.UNAUTHORIZED
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class ServiceNowTableApiAdapterSpec extends Specification {

    static final String URL = 'https://bbh.service-now.com'
    static final String LINK = URL + '/change_request.do?sysparm_query=number=CHG0031001'
    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final String CHANGE_FIELDS = 'sys_id,sys_updated_on,number,short_description,description,start_date,' +
            'end_date,u_validation_start,u_validation_end,u_first_usage,u_downtime_start,u_downtime_end,' +
            'u_requested_for,requested_by,u_department,assignment_group,category,assigned_to,type,u_release,cmdb_ci,' +
            'u_incident,business_service,u_problem,u_affected_clients,u_users_affected,u_l1_manager,u_l2_manager,' +
            'u_business_approver,u_support_approver,u_downtime,test_plan,implementation_plan,u_validation_plan,' +
            'backout_plan,u_first_use_plan,u_secure_coding_ticket,state'
    static final String TASK_FIELDS = 'sys_id,sys_updated_on,number,change_request,assignment_group,assigned_to,' +
            'cmdb_ci,u_platform,u_application,u_packages,u_backout_packages,priority,short_description,description,' +
            'u_additional_comments,expected_start,state,approval'
    static final String APPROVAL_FIELDS = 'sys_id,sys_updated_on,sysapproval,approver,state'

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def proTech = adapter(ProTechFields.DEFAULTS)

    def "the live ProTech says it is connected"() {
        expect:
        proTech.connected()
        proTech.mode() == LIVE
    }

    def "a change is raised as a change_request with the fields of its template, its schedule and its description"() {
        given:
        expect(POST, 'change_request', [sysparm_input_display_value: 'true', sysparm_fields: 'sys_id,number',
                                        sysparm_exclude_reference_link: 'true'])
                .andExpect(header('Authorization', 'Basic ' + 'beadle:secret'.bytes.encodeBase64().toString()))
                .andExpect(body(changeBody()))
                .andRespond(withStatus(CREATED).contentType(APPLICATION_JSON)
                        .body(json(result: [sys_id: 'c1', number: 'CHG0031001'])))

        when:
        def raised = proTech.raise(richChange(number: null, tasks: []))

        then:
        raised == new RaisedChange('CHG0031001', LINK)
        server.verify()
    }

    def "a change task is created on the change it names, with the fields of its details"() {
        given:
        def task = new ChangeTask(null, task(1).details().toBuilder().assignedTo('Ann Lee').build(), null, null,
                null, null, null)
        findChange('sys_id,number')
        expect(POST, 'change_task', [sysparm_input_display_value: 'true', sysparm_fields: 'sys_id,number',
                                     sysparm_exclude_reference_link: 'true'])
                .andExpect(body([change_request    : 'CHG0031001', assignment_group: 'Technology Architecture',
                                 assigned_to       : 'Ann Lee', cmdb_ci: 'CertScanner', u_platform: '',
                                 u_application     : '', u_packages: '', u_backout_packages: '',
                                 priority          : '3 - Moderate',
                                 short_description : 'Task 1 of the CertScanner release',
                                 description       : 'Step 1 of the CertScanner release.',
                                 u_additional_comments: '', expected_start: '']))
                .andRespond(created(sys_id: 't1', number: 'CTASK0041001'))

        when:
        def number = proTech.createTask('CHG0031001', task)

        then:
        number == 'CTASK0041001'
        server.verify()
    }

    def "a release management task carries its platform, application, packages and start"() {
        given:
        def task = new ChangeTask(null, task(1).details().toBuilder().assignmentGroup('Release Management')
                .platform('OpenShift').packages('cert-api:4.2.0').backoutPackages('cert-api:4.1.3').build(),
                at('2026-10-10T06:01:00Z'), null, null, null, null)
        findChange('sys_id,number')
        expect(POST, 'change_task', [:])
                .andExpect(body([change_request    : 'CHG0031001', assignment_group: 'Release Management',
                                 assigned_to       : '', cmdb_ci: 'CertScanner', u_platform: 'OpenShift',
                                 u_application     : 'OCP', u_packages: 'cert-api:4.2.0',
                                 u_backout_packages: 'cert-api:4.1.3', priority: '',
                                 short_description : 'Task 1 of the CertScanner release',
                                 description       : 'Step 1 of the CertScanner release.',
                                 u_additional_comments: '', expected_start: '2026-10-10 06:01:00']))
                .andRespond(created(sys_id: 't3', number: 'CTASK0041003'))

        expect:
        proTech.createTask('CHG0031001', task) == 'CTASK0041003'
    }

    def "a change task for a change ProTech does not have is refused"() {
        given:
        expect(GET, 'change_request', [sysparm_query: 'number=CHG0099999']).andRespond(rows())

        when:
        proTech.createTask('CHG0099999', task(1))

        then:
        def refused = thrown(IllegalStateException)
        refused.message == 'ProTech has no change CHG0099999'
    }

    def "the changes are read back with their fields, state, tasks and approvals; a change ProTech does not have is left out"() {
        given:
        expect(GET, 'change_request', [sysparm_query: 'numberINCHG0031001,CHG0031002', sysparm_fields: CHANGE_FIELDS,
                                       sysparm_limit: '2', sysparm_display_value: 'all',
                                       sysparm_exclude_reference_link: 'true'])
                .andRespond(rows(changeRow()))
        expect(GET, 'change_task', [sysparm_query: 'change_requestINc1^ORDERBYnumber', sysparm_fields: TASK_FIELDS,
                                    sysparm_limit: '100', sysparm_display_value: 'all'])
                .andRespond(rows(taskRow('t3', 'CTASK0041003', '1', 'Open', 'not requested'),
                        taskRow('t1', 'CTASK0041001', '2', 'Work in Progress', 'approved'),
                        taskRow('t2', 'CTASK0041002', '1', 'Open', 'requested')))
        expect(GET, 'sysapproval_approver', [sysparm_query : 'sysapprovalINc1,t3,t1,t2', sysparm_fields: APPROVAL_FIELDS,
                                             sysparm_limit : '80', sysparm_display_value: 'all'])
                .andRespond(rows(approvalRow('c1', 'Olivia Bennett', 'approved'),
                        approvalRow('c1', 'Rebecca Lawson', 'approved'),
                        approvalRow('c1', 'James Carter', 'requested'),
                        approvalRow('c1', 'Change Advisory Board', 'requested'),
                        approvalRow('t1', 'Rebecca Lawson', 'approved'),
                        approvalRow('t1', 'Thomas Ashby', 'requested')))
        def known = raised(tasks: [task(1).remindedBy(new Reminder(RAISED, ['Rebecca Lawson'])), task(2)])

        when:
        def read = proTech.read([known, raised(number: 'CHG0031002')])

        then:
        read.keySet() == ['CHG0031001'] as Set
        def change = read.CHG0031001
        change.id() == 7L
        change.url() == LINK
        change.state() == IMPLEMENTATION
        change.workflow() == [new WorkflowStep(DRAFT, RAISED), new WorkflowStep(IMPLEMENTATION, at('2026-10-09T15:30:00Z'))]
        change.shortDescription() == 'CertScanner CERT 4.2: Expiry alerts and audit'
        change.description() == 'Production release CERT 4.2 of CertScanner, as ProTech holds it.'
        change.schedule() == schedule(installationStart: '2026-10-10T07:00:00Z', installationEnd: '2026-10-10T10:00:00Z')
        change.template().assignmentGroup() == 'Technology Architecture'
        change.template().assignedTo() == 'Ann Lee'
        change.template().type() == EMERGENCY
        change.template().incident() == 'INC0104211'
        change.template().department() == null
        change.template().downtime() == false
        change.template().planning().backoutPlan() == 'Redeploy CERT 4.1.'
        change.template().planning().testSummary() == known.template().planning().testSummary()
        change.template().approvers() == known.template().approvers()
        change.template().jiraProjectKey() == 'CERT'
        change.template().riskAssessment() == known.template().riskAssessment()
        change.approvals() == [new ChangeApproval(BUSINESS, 'Rebecca Lawson', APPROVED, null),
                               new ChangeApproval(L1, 'Olivia Bennett', APPROVED, null),
                               new ChangeApproval(L2, 'James Carter', REQUESTED, null),
                               new ChangeApproval(SUPPORT, 'Jane Smith', APPROVED, null)]
        change.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', 'CTASK0041003']
        change.tasks()*.state() == [WORK_IN_PROGRESS, OPEN, OPEN]
        change.tasks()*.approval() == [APPROVED, REQUESTED, NOT_APPROVED]
        change.tasks()*.approvers() == [['Rebecca Lawson', 'Thomas Ashby'], [], []]
        change.tasks()[0].reminder().sentTo() == ['Rebecca Lawson']
        change.tasks()[0].details().shortDescription() == 'Deploy CertScanner 4.2'
        change.tasks()[2].details().shortDescription() == 'Check the audit trail'
        change.tasks()[2].details().importance() == '2 - High'
        server.verify()
    }

    def "a state no listed value names keeps the state Beadle knew, and the same state adds no workflow step"() {
        given:
        expect(GET, 'change_request', [:]).andRespond(rows(changeRow(state: [value: '42', display_value: 'Odd'])))
        expect(GET, 'change_task', [:]).andRespond(rows())
        expect(GET, 'sysapproval_approver', [sysparm_query: 'sysapprovalINc1']).andRespond(rows())

        when:
        def change = proTech.read([raised(state: CTASK_APPROVAL, workflow: [new WorkflowStep(CTASK_APPROVAL, RAISED)],
                tasks: [])]).CHG0031001

        then:
        change.state() == CTASK_APPROVAL
        change.workflow() == [new WorkflowStep(CTASK_APPROVAL, RAISED)]
        change.tasks() == []
    }

    def "reading changes ProTech does not have asks once and answers nothing"() {
        given:
        expect(GET, 'change_request', [sysparm_query: 'numberINCHG0031001']).andRespond(rows())

        expect:
        proTech.read([raised()]) == [:]
        proTech.read([raised(number: '^;')]) == [:]
        server.verify()
    }

    def "an update patches the change, edits its open tasks, creates the new ones and cancels the dropped ones"() {
        given:
        def change = richChange(tasks: [task(1), task(2), new ChangeTask(null, task(3).details(), null, null, null,
                null, OPEN)])
        findChange('sys_id,number')
        expect(PATCH, 'change_request/c1', [sysparm_input_display_value: 'true', sysparm_fields: 'sys_id'])
                .andExpect(body(changeBody())).andRespond(withSuccess(json(result: [sys_id: 'c1']), APPLICATION_JSON))
        expect(GET, 'change_task', [sysparm_query: 'change_requestINc1^ORDERBYnumber'])
                .andRespond(rows(taskRow('t1', 'CTASK0041001', '1', 'Open', 'requested'),
                        taskRow('t2', 'CTASK0041002', '3', 'Closed Complete', 'approved'),
                        taskRow('t9', 'CTASK0041009', '2', 'Work in Progress', 'approved'),
                        taskRow('t8', 'CTASK0041008', '4', 'Canceled', 'approved')))
        expect(PATCH, 'change_task/t1', [sysparm_input_display_value: 'true'])
                .andExpect(bodyWith(short_description: 'Task 1 of the CertScanner release'))
                .andRespond(withSuccess(json(result: [sys_id: 't1']), APPLICATION_JSON))
        expect(POST, 'change_task', [:])
                .andExpect(bodyWith(change_request: 'CHG0031001', short_description: 'Task 3 of the CertScanner release'))
                .andRespond(created(sys_id: 't10', number: 'CTASK0041010'))
        expect(PATCH, 'change_task/t9', [sysparm_input_display_value: 'false'])
                .andExpect(body([state: '4'])).andRespond(withSuccess(json(result: [sys_id: 't9']), APPLICATION_JSON))

        when:
        proTech.update(change)

        then:
        server.verify()
    }

    def "a reminder to the #role.label() comments on their requested approval of the change"() {
        given:
        findChange('sys_id,u_l1_manager,u_l2_manager,u_business_approver,u_support_approver,number')
        expect(GET, 'sysapproval_approver', [sysparm_query: 'sysapproval=c1^stateINrequested',
                                             sysparm_fields: APPROVAL_FIELDS, sysparm_limit: '20'])
                .andRespond(rows(approvalRow('c1', 'Rebecca Lawson', 'requested', 'a1'),
                        approvalRow('c1', 'James Carter', 'requested', 'a2')))
        expect(PATCH, 'sysapproval_approver/' + approval, [sysparm_input_display_value: 'true'])
                .andExpect(body([comments: "Beadle reminds you that CHG0031001 waits for your approval as its ${role.label()}.".toString()]))
                .andRespond(withSuccess(json(result: [sys_id: approval]), APPLICATION_JSON))

        when:
        def reminded = proTech.remind('CHG0031001', ApprovalRef.of(role))

        then:
        reminded == [approver]
        server.verify()

        where:
        role     | approver         | approval
        BUSINESS | 'Rebecca Lawson' | 'a1'
        L2       | 'James Carter'   | 'a2'
    }

    def "a reminder on a change task comments on every requested approval of the task"() {
        given:
        findChange('sys_id,u_l1_manager,u_l2_manager,u_business_approver,u_support_approver,number')
        expect(GET, 'change_task', [sysparm_query: 'change_request=c1^number=CTASK0041001', sysparm_limit: '1'])
                .andRespond(rows([sys_id: field('t1'), number: field('CTASK0041001')]))
        expect(GET, 'sysapproval_approver', [sysparm_query: 'sysapproval=t1^stateINrequested'])
                .andRespond(rows(approvalRow('t1', 'Rebecca Lawson', 'requested', 'a1'),
                        approvalRow('t1', 'Thomas Ashby', 'requested', 'a2')))
        ['a1', 'a2'].each {
            expect(PATCH, 'sysapproval_approver/' + it, [:])
                    .andExpect(body([comments: 'Beadle reminds you that the change task CTASK0041001 of CHG0031001 waits for your approval.']))
                    .andRespond(withSuccess(json(result: [sys_id: it]), APPLICATION_JSON))
        }

        expect:
        proTech.remind('CHG0031001', ApprovalRef.ofTask('CTASK0041001')) == ['Rebecca Lawson', 'Thomas Ashby']
    }

    def "a reminder with nobody waiting or on a task ProTech does not have is refused"() {
        given:
        findChange('sys_id,u_l1_manager,u_l2_manager,u_business_approver,u_support_approver,number')
        if (approval.task()) {
            expect(GET, 'change_task', [:]).andRespond(rows())
        } else {
            expect(GET, 'sysapproval_approver', [:]).andRespond(rows(approvalRow('c1', 'James Carter', 'requested')))
        }

        when:
        proTech.remind('CHG0031001', approval)

        then:
        def refused = thrown(IllegalStateException)
        refused.message == message

        where:
        approval                           || message
        ApprovalRef.of(BUSINESS)           || 'ProTech has nobody to remind on CHG0031001'
        ApprovalRef.ofTask('CTASK0041009') || 'ProTech has no change task CTASK0041009 on CHG0031001'
    }

    def "the #kind lookup searches #table by its value and detail"() {
        given:
        expect(GET, table, [sysparm_query: query, sysparm_fields: fields, sysparm_limit: '20',
                            sysparm_display_value: 'true', sysparm_exclude_reference_link: 'true'])
                .andRespond(withSuccess(json(result: [found, [(fields.tokenize(',')[0]): '']]), APPLICATION_JSON))

        expect:
        proTech.find(kind, text, 20) == [lookup]

        where:
        kind                           | text    | table            | fields                     | query                                                                                    | found                                                       || lookup
        LookupKind.USERS               | 'smith' | 'sys_user'       | 'name,email'               | 'active=true^nameLIKEsmith^ORemailLIKEsmith^ORDERBYname'                                 | [name: 'Jane Smith', email: 'jane.smith@bbh.com']           || new Lookup('Jane Smith', 'jane.smith@bbh.com')
        LookupKind.DEPARTMENTS         | ''      | 'cmn_department' | 'name,cost_center'         | 'ORDERBYname'                                                                            | [name: 'Custody', cost_center: '4410']                      || new Lookup('Custody', '4410')
        LookupKind.ASSIGNMENT_GROUPS   | 'ops'   | 'sys_user_group' | 'name,description'         | 'active=true^nameLIKEops^ORdescriptionLIKEops^ORDERBYname'                               | [name: 'Network Operations', description: 'Networks']       || new Lookup('Network Operations', 'Networks')
        LookupKind.RELEASES            | 'cert'  | 'rm_release'     | 'short_description,number' | 'short_descriptionLIKEcert^ORnumberLIKEcert^ORDERBYshort_description'                    | [short_description: 'CertScanner 4.2', number: 'RLSE0001'] || new Lookup('CertScanner 4.2', 'RLSE0001')
        LookupKind.CONFIGURATION_ITEMS | 'cert'  | 'cmdb_ci'        | 'name,sys_class_name'      | 'nameLIKEcert^ORsys_class_nameLIKEcert^ORDERBYname'                                      | [name: 'CertScanner', sys_class_name: 'Application']        || new Lookup('CertScanner', 'Application')
        LookupKind.INCIDENTS           | '0104'  | 'incident'       | 'number,short_description' | 'numberLIKE0104^ORshort_descriptionLIKE0104^ORDERBYnumber'                               | [number: 'INC0104211', short_description: 'Login']          || new Lookup('INC0104211', 'Login')
        LookupKind.PROBLEMS            | 'pool'  | 'problem'        | 'number,short_description' | 'numberLIKEpool^ORshort_descriptionLIKEpool^ORDERBYnumber'                               | [number: 'PRB0040112', short_description: 'Pool']           || new Lookup('PRB0040112', 'Pool')
        LookupKind.CLIENTS             | 'a^b'   | 'core_company'   | 'name,country'             | 'customer=true^nameLIKEa^^b^ORcountryLIKEa^^b^ORDERBYname'                               | [name: 'Alder Ridge Pension Fund', country: 'USA']         || new Lookup('Alder Ridge Pension Fund', 'USA')
    }

    def "a refusal of ProTech names its message, while ProTech out of reach or signing in in vain is a failure to reach it"() {
        given:
        expect(POST, 'change_request', [:]).andRespond(response)

        when:
        proTech.raise(richChange(number: null))

        then:
        def failed = thrown(type)
        failed.message == message

        where:
        response << [withStatus(BAD_REQUEST).contentType(APPLICATION_JSON).body(json(status: 'failure',
                error: [message: 'Operation Failed', detail: 'Data Policy Exception: Category is mandatory'])),
                     withStatus(BAD_REQUEST),
                     withStatus(UNAUTHORIZED).contentType(APPLICATION_JSON).body(json(status: 'failure',
                             error: [message: 'User Not Authenticated', detail: 'Required to provide Auth information'])),
                     withServerError(),
                     withStatus(CREATED).contentType(APPLICATION_JSON).body('{"result":{}}')]
        type << [IllegalStateException, IllegalStateException, UncheckedIOException, UncheckedIOException,
                 UncheckedIOException]
        message << ['ProTech refused to raise the change: Operation Failed (Data Policy Exception: Category is mandatory)',
                    'ProTech refused to raise the change: Bad Request',
                    'ProTech could not raise the change: 401 User Not Authenticated (Required to provide Auth information)',
                    'ProTech could not raise the change: 500 Internal Server Error',
                    NO_NUMBER]
    }

    def "ProTech out of reach is a failure to reach it with the reason"() {
        given:
        expect(GET, 'sys_user', [:]).andRespond(withException(new SocketTimeoutException('Read timed out')))

        when:
        proTech.find(LookupKind.USERS, 'smith', 20)

        then:
        def failed = thrown(UncheckedIOException)
        failed.message == 'ProTech could not look up the users: I/O error on GET request for ' +
                '"https://bbh.service-now.com/api/now/table/sys_user": Read timed out'
    }

    def "the configured field names are the ones written, and an empty name leaves a field out"() {
        given:
        def custom = adapter(new ProTechFields(copy([table: 'x_bbh_change', department: '', release: 'x_bbh_release',
                                                    privilegedAccess: '', riskAssessment: ''], ChangeFields.DEFAULTS),
                null, null, null, null, null, null))
        expect(POST, 'x_bbh_change', [:])
                .andExpect { ClientHttpRequest request ->
                    def sent = new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) as Map
                    assert sent.x_bbh_release == 'CERT 4.2'
                    assert !sent.containsKey('u_department') && !sent.containsKey('u_release')
                    assert !sent.containsKey('u_privileged_access') && !sent.containsKey('u_risk_assessment')
                    assert sent.short_description == 'CertScanner CERT 4.2: Expiry alerts'
                }
                .andRespond(created(sys_id: 'c1', number: 'CHG0031001'))

        expect:
        custom.raise(richChange(number: null)).url() == URL + '/x_bbh_change.do?sysparm_query=number=CHG0031001'
    }

    def "a poll asks for the changes, change tasks and approvals ProTech updated since the last look"() {
        given:
        def since = at('2026-10-15T08:58:00Z')
        def numbers = 'CHG0031001,CHG0031002,CHG0031003,CHG0031004'
        expect(GET, 'change_task', [sysparm_query: 'change_request.numberIN' + numbers,
                                    sysparm_fields: 'sys_id,sys_updated_on,number,change_request', sysparm_limit: '400',
                                    sysparm_display_value: 'all'])
                .andRespond(rows(pollRow('t1', '2026-10-15 08:59:00', [number: field('CTASK0041001'), change_request: field('c1', 'CHG0031001')]),
                        pollRow('t2', '2026-10-14 08:00:00', [number: field('CTASK0041002'), change_request: field('c2', 'CHG0031002')]),
                        pollRow('t3', '2026-10-15 08:59:30', [number: field('CTASK0041003'), change_request: field('c3', 'CHG0031003')])))
        expect(GET, 'change_request', [sysparm_query: "numberIN$numbers^sys_updated_on>2026-10-15 08:58:00".toString(),
                                       sysparm_fields: 'sys_id,sys_updated_on,number,state', sysparm_limit: '4',
                                       sysparm_display_value: 'all'])
                .andRespond(rows(pollRow('c1', '2026-10-15 08:59:10', [number: field('CHG0031001'), state: field('-1', 'Implement')])))
        expect(GET, 'sysapproval_approver', [sysparm_query: "sysapproval.numberIN$numbers,CTASK0041001,CTASK0041002,CTASK0041003^sys_updated_on>2026-10-15 08:58:00".toString(),
                                             sysparm_fields: 'sys_id,sys_updated_on,sysapproval', sysparm_limit: '140',
                                             sysparm_display_value: 'all'])
                .andRespond(rows(pollRow('a1', '2026-10-15 08:58:30', [sysapproval: field('t2', 'CTASK0041002')]),
                        pollRow('a2', '2026-10-15 08:59:40', [sysapproval: field('c2', 'CHG0031002')]),
                        pollRow('a3', '2026-10-15 08:59:20', [sysapproval: field('x9', 'CTASK0099999')])))

        when:
        def activity = proTech.activity(numbers.split(',') as List, since)

        then:
        activity == [CHG0031001: new ChangeActivity('CHG0031001', IMPLEMENTATION, at('2026-10-15T08:59:10Z'), null, at('2026-10-15T08:59:00Z')),
                     CHG0031002: new ChangeActivity('CHG0031002', null, null, at('2026-10-15T08:59:40Z'), null),
                     CHG0031003: new ChangeActivity('CHG0031003', null, null, null, at('2026-10-15T08:59:30Z'))]
        server.verify()
    }

    def "the changes are polled in batches, every beadle.protech.poll, and a number ProTech cannot read is not asked"() {
        given:
        int batch = ServiceNowTableApiAdapter.POLL_BATCH
        List<String> numbers = (1..batch + 1).collect { 'CHG00' + (31000 + it) }
        expect(GET, 'change_task', [sysparm_query: 'change_request.numberIN' + numbers.take(batch).join(',')]).andRespond(rows())
        expect(GET, 'change_request', [:]).andRespond(rows())
        expect(GET, 'sysapproval_approver', [:]).andRespond(rows())
        expect(GET, 'change_task', [sysparm_query: 'change_request.numberIN' + numbers.last()]).andRespond(rows())
        expect(GET, 'change_request', [sysparm_query: 'numberIN' + numbers.last()]).andRespond(rows())
        expect(GET, 'sysapproval_approver', [sysparm_query: 'sysapproval.numberIN' + numbers.last()]).andRespond(rows())

        expect:
        proTech.activity(numbers + ['^;', ' '], null) == [:]
        proTech.activity(['^;'], NOW) == [:]
        proTech.pollEvery() == Duration.ofMinutes(1)
        new ServiceNowTableApiAdapter(new ProTechProperties(URL, 'beadle', 'secret', Duration.ofSeconds(30), null),
                builder, Clock.fixed(NOW, UTC)).pollEvery() == Duration.ofSeconds(30)
        server.verify()
    }

    private ServiceNowTableApiAdapter adapter(ProTechFields fields) {
        new ServiceNowTableApiAdapter(new ProTechProperties(URL + '/', 'beadle', 'secret', null, fields), builder,
                Clock.fixed(NOW, UTC))
    }

    private void findChange(String fields) {
        expect(GET, 'change_request', [sysparm_query: 'number=CHG0031001', sysparm_fields: fields, sysparm_limit: '1',
                                       sysparm_display_value: 'all'])
                .andRespond(rows(changeRow()))
    }

    private def expect(HttpMethod verb, String path, Map<String, String> parameters) {
        server.expect(method(verb)).andExpect({ ClientHttpRequest request ->
            assert request.URI.toString().startsWith("$URL/api/now/table/$path?")
            Map<String, String> sent = request.URI.rawQuery.split('&').collectEntries {
                it.split('=', 2).collect { URLDecoder.decode(it, UTF_8) }
            }
            parameters.each { name, value -> assert sent[name] == value }
        } as RequestMatcher)
    }

    private static RequestMatcher body(Map expected) {
        { ClientHttpRequest request ->
            assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) == expected
        } as RequestMatcher
    }

    private static RequestMatcher bodyWith(Map expected) {
        { ClientHttpRequest request ->
            def sent = new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) as Map
            expected.each { name, value -> assert sent[name] == value }
        } as RequestMatcher
    }

    private static rows(Map... rows) {
        withSuccess(json(result: rows as List), APPLICATION_JSON)
    }

    private static created(Map result) {
        withStatus(CREATED).contentType(APPLICATION_JSON).body(json(result: result))
    }

    private static String json(Map body) {
        JsonOutput.toJson(body)
    }

    private static Map field(String value, String display = value) {
        [value: value, display_value: display]
    }

    private static ProductionChange richChange(Map changes = [:]) {
        raised([template: template(release: 'CERT 4.2', requestedFor: 'Mateusz Matan', requestedBy: 'Mateusz Matan',
                department: 'Corporate Technology', assignedTo: 'Ann Lee', incident: 'INC0104211',
                problem: 'PRB0040112', directBusinessService: 'Certificate Services', affectedClients: 'No clients',
                usersAffected: 'Corporate Technology staff', secureCodingTicket: 'SCP-1001',
                privilegedAccess: privileged(1))] + changes)
    }

    private static Map changeBody() {
        [short_description    : 'CertScanner CERT 4.2: Expiry alerts',
         description          : 'Production release CERT 4.2 of CertScanner.',
         start_date           : '2026-10-10 06:00:00', end_date: '2026-10-10 10:00:00',
         u_validation_start   : '2026-10-10 10:00:00', u_validation_end: '2026-10-10 11:00:00',
         u_first_usage        : '2026-10-12 08:00:00', u_downtime_start: '', u_downtime_end: '',
         u_requested_for      : 'Mateusz Matan', requested_by: 'Mateusz Matan', u_department: 'Corporate Technology',
         assignment_group     : 'Technology Architecture', category: 'Application', assigned_to: 'Ann Lee',
         type                 : 'Standard', u_release: 'CERT 4.2', cmdb_ci: 'CertScanner', u_incident: 'INC0104211',
         business_service     : 'Certificate Services', u_problem: 'PRB0040112', risk: 'Moderate',
         u_affected_clients   : 'No clients', u_users_affected: 'Corporate Technology staff',
         u_l1_manager         : 'Olivia Bennett', u_l2_manager: 'James Carter', u_business_approver: 'Rebecca Lawson',
         u_support_approver   : 'Jane Smith', u_downtime: 'false',
         test_plan            : 'Unit, smoke, regression and performance tests and the security scans of the DevSecOps pipeline passed on QC.',
         implementation_plan  : '1. Carry out the change tasks in the order listed.\n2. Run the smoke tests of the DevSecOps pipeline against production.\n3. Confirm with the business owner and close the change tasks.',
         u_validation_plan    : 'Run the smoke tests of the DevSecOps pipeline against production and check the monitoring of the product.',
         backout_plan         : 'Redeploy the previous release from Nexus with the same deployment job and confirm with the smoke tests.',
         u_first_use_plan     : 'The business owner confirms the first use of the release in production.',
         u_privileged_access  : 'Privileged access needed for: User 1 (adm_user1).',
         u_risk_assessment    : 'Number of BBH workgroups impacted: Single\nComplexity of the change: Simple\n' +
                 'Number of BBH users impacted: 5-25\nComplexity of validation: Simple\n' +
                 'Number of applications impacted: Single\nBackout testing & duration: Less than 30 minutes\n' +
                 'Number of impacted clients outside BBH: No clients\nPlatform status: Existing\nBusiness impact: Low',
         u_secure_coding_ticket: 'SCP-1001']
    }

    private static Map changeRow(Map changes = [:]) {
        [sys_id             : field('c1'), sys_updated_on: field('2026-10-09 15:30:00', '09/10/2026 17:30:00'),
         number             : field('CHG0031001'),
         short_description  : field('CertScanner CERT 4.2: Expiry alerts and audit'),
         description        : field('Production release CERT 4.2 of CertScanner, as ProTech holds it.'),
         start_date         : field('2026-10-10 07:00:00', '10/10/2026 09:00:00'),
         end_date           : field('2026-10-10 10:00:00'), u_validation_start: field('2026-10-10 10:00:00'),
         u_validation_end   : field('2026-10-10 11:00:00'), u_first_usage: field('2026-10-12 08:00:00'),
         u_downtime_start   : field(''), u_downtime_end: field(''),
         assignment_group   : field('a9f0', 'Technology Architecture'), assigned_to: field('u42', 'Ann Lee'),
         category           : field('application', 'Application'), type: field('emergency', 'Emergency'),
         u_department       : field(''), u_incident: field('i1', 'INC0104211'), u_downtime: field('false'),
         u_l1_manager       : field('u1', 'Olivia Bennett'), u_l2_manager: field('u2', 'James Carter'),
         u_business_approver: field('u3', 'Rebecca Lawson'), u_support_approver: field('u4', 'Jane Smith'),
         backout_plan       : field('Redeploy CERT 4.1.'), state: field('-1', 'Implement')] + changes
    }

    private static Map taskRow(String id, String number, String state, String stateLabel, String approval) {
        Map details = [t1: [short_description: field('Deploy CertScanner 4.2')],
                       t3: [short_description: field('Check the audit trail'), description: field('Open it.'),
                            assignment_group : field('g1', 'Technology Architecture'), priority: field('2', '2 - High')]]
        [sys_id: field(id), number: field(number), change_request: field('c1', 'CHG0031001'),
         state : field(state, stateLabel), approval: field(approval, approval.capitalize())] + (details[id] ?: [:])
    }

    private static Map pollRow(String id, String updated, Map fields) {
        [sys_id: field(id), sys_updated_on: field(updated)] + fields
    }

    private static Map approvalRow(String approving, String approver, String state, String id = 'a0') {
        [sys_id  : field(id), sysapproval: field(approving, approving == 'c1' ? 'CHG0031001' : 'CTASK'),
         approver: field(approver.toLowerCase().replace(' ', '.'), approver), state: field(state, state.capitalize())]
    }
}

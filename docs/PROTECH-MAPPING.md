# How Beadle-ProTech maps a change to ProTech

ProTech is BBH's ServiceNow. When `beadle.protech.instance-url` (`PROTECH_URL`) names it, Beadle-ProTech talks to it
through `LiveProTechAdapter`, which uses the ServiceNow Table API (`/api/now/table/{table}`) as the Beadle
service account (`beadle.protech.username` / `beadle.protech.password`, basic authentication, connect timeout 5 s, read
timeout 10 s, so that a slow ProTech is answered `502` well within the 30 s Beadle-GUI waits for Beadle-ProTech). This
page lists which ProTech table and field holds each field of a Beadle change, and which ProTech values stand for each
state.

The mapping is a best effort made without access to the real instance. Every table name, field name and state value
below is a property under `beadle.protech.fields.*` (class `ProTechFields`) with the default shown, so the real
instance can be matched in the configuration map or the environment, without a code change:

- a property that is not set keeps its default;
- a property set to an empty value (`beadle.protech.fields.change.business-service=`) leaves that field out of every call:
  Beadle neither writes it nor reads it, and keeps its own value;
- a field ProTech does not have is ignored by ProTech when written and missing from its answers, and Beadle then keeps
  its own value, so a wrong default does no harm beyond not reaching ProTech.

In the environment the properties are written in upper case with underscores, for example
`BEADLE_PROTECH_FIELDS_CHANGE_RELEASE=u_bbh_release` or `BEADLE_PROTECH_FIELDS_CHANGESTATES_IMPLEMENTATION=-1,Implement`.

## How the calls look

| Operation of the API | Calls to ProTech |
|---|---|
| `POST /api/protech/changes` (create) | `POST /api/now/table/change_request?sysparm_input_display_value=true&sysparm_fields=sys_id,number` with the fields of the change below; answers the new `number` and the link `{url}/change_request.do?sysparm_query=number={number}` |
| `POST /api/protech/changes/{number}/tasks` | `GET change_request?sysparm_query=number={number}`, then `POST /api/now/table/change_task` with the fields of the task and `change_request={number}`; answers the new `number` |
| `POST /api/protech/changes/read` | `GET change_request?sysparm_query=numberIN{numbers}&sysparm_display_value=all`, `GET change_task?sysparm_query=change_requestIN{sys_ids}^ORDERBYnumber`, `GET sysapproval_approver?sysparm_query=sysapprovalIN{sys_ids of the changes and tasks}`, each list asked 50 values at a time so that no URL grows past a request line; a change ProTech does not have is left out of the answer |
| `PUT /api/protech/changes/{number}` | `GET change_request` by number, `PATCH /api/now/table/change_request/{sys_id}` with the fields of the change, `GET change_task` of the change, then per task: `PATCH change_task/{sys_id}` for a task that is neither closed nor canceled, `POST change_task` for a task without a number (or one ProTech no longer has), and `PATCH change_task/{sys_id}` with `state` = the first CANCELED value for an open task Beadle no longer lists |
| `POST /api/protech/changes/{number}/reminders` | `GET change_request` by number with the approver fields; for a role: `GET sysapproval_approver?sysparm_query=sysapproval={change sys_id}^stateINrequested` and the approval whose approver is the person named in that role; for a task: `GET change_task?sysparm_query=change_request={sys_id}^number={task}` and its requested approvals; then `PATCH sysapproval_approver/{sys_id}` with a comment per approval (ServiceNow's approval notification e-mails the comment to the approver); answers the people reminded |
| `GET /api/protech/lookups/{kind}` | `GET {table}?sysparm_query={filter}^{value}LIKE{q}^OR{detail}LIKE{q}^ORDERBY{value}&sysparm_fields={value},{detail}&sysparm_limit={limit}&sysparm_display_value=true` |
| the poll, every `beadle.protech.poll` (default `PT1M`), of the open changes Beadle created or read, 50 at a time | `GET change_task?sysparm_query=change_request.numberIN{numbers}` (number, change and `sys_updated_on` of every task of the changes), `GET change_request?sysparm_query=numberIN{numbers}^sys_updated_on>{since}` (number, state, `sys_updated_on`), `GET sysapproval_approver?sysparm_query=sysapproval.numberIN{numbers of the changes and their tasks}^sys_updated_on>{since}`, 50 numbers at a time; `{since}` is the last look minus one minute, as `yyyy-MM-dd HH:mm:ss` in UTC |

Reads ask for every field with its value and its display value (`sysparm_display_value=all`). Beadle takes the
display value of a reference or choice field (a person, a group, a configuration item, a category: what a user sees)
and the value of a date-time field (`yyyy-MM-dd HH:mm:ss` in UTC); a value that is no date-time is read as empty and
logged once as a warning. Writes give display values
(`sysparm_input_display_value=true`), so ProTech resolves a person, group or configuration item by its name and a
choice by its label; dates are written as `yyyy-MM-dd HH:mm:ss`. **The Beadle service account of ProTech must
therefore have the time zone UTC and the date format `yyyy-MM-dd` with the time format `HH:mm:ss`**, which are the
ServiceNow system defaults. Canceling a task is the only write with a raw value (`sysparm_input_display_value=false`).

From the poll Beadle-ProTech calls Beadle-GUI back once per change that moved past what Beadle-GUI knows:
`STATE_CHANGED` when the `state` maps to another state than the one Beadle-GUI knows, otherwise `APPROVAL_CHANGED` when
an approval of the change or of one of its tasks was updated after the last move Beadle-GUI knows of, otherwise
`TASK_CHANGED` when one of its tasks was. What Beadle-GUI did itself (a task created, an update, a reminder) moves
that mark to the moment it did it, so it is not called back for its own action. That moment is taken from the clock
of Beadle-ProTech while `sys_updated_on` comes from ProTech's, so the comparison needs the two clocks synchronised
(NTP): ProTech ahead by `s` seconds calls Beadle-GUI back for its own actions for `s` seconds after each, ProTech
behind by `s` seconds leaves an approval or task move within `s` seconds after an action of Beadle-GUI unreported
until the change moves again. The minute the poll reaches back (`{since}`) only keeps updates between two looks from
being missed; it does not make up for a skew.

A refusal of ProTech (`4xx` other than `401`, `403`, `407`, `408`, `429`) is answered `409` with ProTech's status and
message (`error.message` and `error.detail` of its answer). ProTech out of reach, a failed sign-in or a `5xx` answer is
answered `502` with the reason.

## The change: `change_request` (`beadle.protech.fields.change.*`)

| Beadle field (`Change` JSON) | Property | Default ServiceNow field | Notes |
|---|---|---|---|
| (the table) | `table` | `change_request` | |
| `number` | `number` | `number` | read only; ProTech numbers the change |
| `shortDescription` | `short-description` | `short_description` | |
| `description` | `description` | `description` | |
| `schedule.installationStart` | `installation-start` | `start_date` | planned start |
| `schedule.installationEnd` | `installation-end` | `end_date` | planned end |
| `schedule.validationStart` | `validation-start` | `u_validation_start` | |
| `schedule.validationEnd` | `validation-end` | `u_validation_end` | |
| `schedule.firstUsage` | `first-usage` | `u_first_usage` | |
| `schedule.downtimeStart` | `downtime-start` | `u_downtime_start` | |
| `schedule.downtimeEnd` | `downtime-end` | `u_downtime_end` | |
| `template.requestedFor` | `requested-for` | `u_requested_for` | person |
| `template.requestedBy` | `requested-by` | `requested_by` | person |
| `template.businessService` | `business-service` | `u_department` | the business service, from `cmn_department` |
| `template.assignmentGroup` | `assignment-group` | `assignment_group` | `sys_user_group` |
| `template.category` | `category` | `category` | choice label, for example `Application` |
| `template.assignedTo` | `assigned-to` | `assigned_to` | person |
| `template.type` | `type` | `type` | written as the label (`Standard`, `Emergency`, `Business Critical`, `Model`), read back by label or name |
| `template.release` | `release` | `u_release` | the release of the lookups |
| `template.configurationItem` | `configuration-item` | `cmdb_ci` | |
| `template.incident` | `incident` | `u_incident` | incident number |
| `template.directBusinessService` | `direct-business-service` | `business_service` | |
| `template.problem` | `problem` | `u_problem` | problem number |
| `template.risk` | `risk` | `risk` | written only: Beadle derives the risk from the risk assessment |
| `template.affectedClients` | `affected-clients` | `u_affected_clients` | |
| `template.usersAffected` | `users-affected` | `u_users_affected` | |
| `template.approvers.l1Manager` | `l1-manager` | `u_l1_manager` | person; names the L1 approver |
| `template.approvers.l2Manager` | `l2-manager` | `u_l2_manager` | person; names the L2 approver |
| `template.approvers.businessApprover` | `business-approver` | `u_business_approver` | person |
| `template.approvers.supportApprover` | `support-approver` | `u_support_approver` | person |
| `template.downtime` | `downtime` | `u_downtime` | `true` / `false` |
| `template.planning.testSummary` | `test-summary` | `test_plan` | |
| `template.planning.implementationPlan` | `implementation-plan` | `implementation_plan` | |
| `template.planning.validationPlan` | `validation-plan` | `u_validation_plan` | |
| `template.planning.backoutPlan` | `backout-plan` | `backout_plan` | |
| `template.planning.firstUsePlan` | `first-use-plan` | `u_first_use_plan` | |
| `template.privilegedAccess` | `privileged-access` | `u_privileged_access` | written only, as text: `Privileged access needed for: Ann Lee (adm_alee).` |
| `template.riskAssessment` | `risk-assessment` | `u_risk_assessment` | written only, one line per question: `Complexity of the change: Simple` |
| `template.secureCodingTicket` | `secure-coding-ticket` | `u_secure_coding_ticket` | the CyberTrack key |
| `state` | `state` | `state` | see the states below |
| `url` | | | `{beadle.protech.instance-url}/change_request.do?sysparm_query=number={number}` |
| `workflow` | | `sys_updated_on` | Beadle's workflow plus a step for the state ProTech holds, entered at `sys_updated_on`, when it differs from Beadle's last step |
| `approvals` | | `sysapproval_approver` | see the approvals below |

The other fields of a change (`id`, `productId`, `fixVersion`, `epicKeys`, `syncedAt`, `update`, ...) are Beadle's
own and come back as Beadle sent them; `template.jiraProjectKey`, `template.timing` and `template.secureCoding` are not
written to ProTech.

## The change tasks: `change_task` (`beadle.protech.fields.task.*`)

| Beadle field (`ChangeTask` JSON) | Property | Default ServiceNow field | Notes |
|---|---|---|---|
| (the table) | `table` | `change_task` | |
| `number` | `number` | `number` | read only |
| (its change) | `change` | `change_request` | written as the change number |
| `details.assignmentGroup` | `assignment-group` | `assignment_group` | |
| `details.assignedTo` | `assigned-to` | `assigned_to` | |
| `details.configurationItem` | `configuration-item` | `cmdb_ci` | |
| `details.platform` | `platform` | `u_platform` | Release Management tasks |
| `details.application` | `application` | `u_application` | Release Management tasks |
| `details.packages` | `packages` | `u_packages` | Release Management tasks |
| `details.backoutPackages` | `backout-packages` | `u_backout_packages` | Release Management tasks |
| `details.importance` | `importance` | `priority` | label `1 - Critical` ... `5 - Planning`, the labels of ServiceNow's priority |
| `details.shortDescription` | `short-description` | `short_description` | |
| `details.description` | `description` | `description` | |
| `details.additionalComments` | `additional-comments` | `u_additional_comments` | a plain field, so that it reads back as written |
| `start` | `start` | `expected_start` | Release Management tasks |
| `state` | `state` | `state` | see the states below |
| `approval` | `approval` | `approval` | used when the task has no `sysapproval_approver` records |
| `approvers` | | `sysapproval_approver.approver` | the people asked to approve the task |

## The approvals: `sysapproval_approver` (`beadle.protech.fields.approval.*`)

| What | Property | Default ServiceNow field |
|---|---|---|
| (the table) | `table` | `sysapproval_approver` |
| the change or task approved | `approving` | `sysapproval` |
| the approver | `approver` | `approver` |
| the state of the approval | `state` | `state` |
| the comment that carries a reminder | `comments` | `comments` |

The approval of a role (`BUSINESS`, `L1`, `L2`, `SUPPORT`) is the approval record of the change whose approver is the
person named in that role (`template.approvers`); when the change has no such record, the role counts as approved
once the change moved past the stage of that role, requested in that stage and not approved before it, as in the demo.
The approval of a task combines its approval records: approved when one approver approved, requested when one is
asked, otherwise not approved; without records the task's own `approval` field decides.

## States

A ProTech value matches a state of Beadle when it equals, ignoring case, one of the values listed for that state,
compared with both the value and the display value of the field; the first state that lists it wins. A value no state
lists keeps the state Beadle knew. Beadle never writes the state of a change; it writes the state of a task only to
cancel it, with the first value listed for `CANCELED`.

`beadle.protech.fields.change-states.<state>` (`change_request.state`):

| `ChangeState` | Default values | ServiceNow base system |
|---|---|---|
| `DRAFT` | `-5`, `New`, `Draft` | `-5` New |
| `BUSINESS_APPROVAL` | `Business Approval` | (BBH state) |
| `PRIMARY_APPROVAL` | `-4`, `Assess`, `Primary Approval` | `-4` Assess |
| `SECONDARY_APPROVAL` | `Secondary Approval` | (BBH state) |
| `SUPPORT_APPROVAL` | `-3`, `Authorize`, `Support Approval` | `-3` Authorize |
| `CTASK_APPROVAL` | `CTask Approval` | (BBH state) |
| `ESCALATED_APPROVAL` | `Escalated Approval` | (BBH state) |
| `IMPLEMENTATION` | `-2`, `Scheduled`, `-1`, `Implement`, `In Progress` | `-2` Scheduled, `-1` Implement |
| `CLOSED` | `3`, `Closed`, `0`, `Review`, `4`, `Canceled` | `0` Review, `3` Closed, `4` Canceled |

`beadle.protech.fields.task-states.<state>` (`change_task.state`):

| `TaskState` | Default values |
|---|---|
| `OPEN` | `1`, `Open`, `-5`, `Pending` |
| `WORK_IN_PROGRESS` | `2`, `Work in Progress`, `In Progress` |
| `CLOSED` | `3`, `Closed Complete`, `Closed`, `7`, `Closed Skipped` |
| `CANCELED` | `4`, `Canceled`, `Closed Incomplete` (the first is written to cancel a task) |

`beadle.protech.fields.approval-states.<state>` (`sysapproval_approver.state` and `change_task.approval`):

| `ApprovalState` | Default values |
|---|---|
| `REQUESTED` | `requested` |
| `APPROVED` | `approved`, `not_required` |
| `NOT_APPROVED` | every other value (`not requested`, `rejected`, `cancelled`, ...) |

## Lookups (`beadle.protech.fields.lookups.<kind>.*`)

Each kind names its `table`, the field shown as the `value`, the field shown as the `detail` and a `filter` in the
encoded query syntax that every search adds (empty for none). The text searched for is matched with `LIKE` against
the value and the detail; a `^` in it is doubled, as the encoded query syntax wants.

| Kind | `table` | `value` | `detail` | `filter` |
|---|---|---|---|---|
| `users` | `sys_user` | `name` | `email` | `active=true` |
| `business-services` | `cmn_department` | `name` | `cost_center` | |
| `assignment-groups` | `sys_user_group` | `name` | `description` | `active=true` |
| `releases` | `rm_release` | `short_description` | `number` | |
| `configuration-items` | `cmdb_ci` | `name` | `sys_class_name` | |
| `incidents` | `incident` | `number` | `short_description` | |
| `problems` | `problem` | `number` | `short_description` | |
| `clients` | `core_company` | `name` | `country` | `customer=true` |

## The webhook (optional)

The poll finds every move within `beadle.protech.poll`. To have Beadle-GUI hear of a move at once, ProTech can call
`POST {Beadle-ProTech}/api/protech/webhook` with `{"number": "CHG0001234"}` and `Authorization: Bearer
<PROTECH_WEBHOOK_TOKEN>` whenever it updates a change, a change task or an approval: the number is always the one of
the change (for a task, of its change; for an approval, of the change it or its task belongs to). Beadle-ProTech
then looks at that change as the poll does (the same three queries, for that change only) and calls Beadle-GUI back
if it moved past what Beadle-GUI knows; it answers `202`, also for a change it does not follow, and leaves the poll
as it was. A move reported by both the webhook and the poll is called back once.

In ProTech this is an asynchronous business rule after insert and update on `change_request`, `change_task` and
`sysapproval_approver` (for the approvals, only of a change request or a change task), which sends an outbound REST
message, for example:

```javascript
var message = new sn_ws.RESTMessageV2();
message.setEndpoint(gs.getProperty('x_bbh.beadle_protech.webhook_url'));   // https://.../api/protech/webhook
message.setHttpMethod('POST');
message.setRequestHeader('Content-Type', 'application/json');
message.setRequestHeader('Authorization', 'Bearer ' + gs.getProperty('x_bbh.beadle_protech.webhook_token')); // password2
message.setRequestBody(JSON.stringify({number: changeNumber}));             // the number of the change
message.executeAsync();
```

The webhook token is `beadle.protech.webhook-token` (`PROTECH_WEBHOOK_TOKEN`, in the secret `beadle-protech-account`):
it opens `POST /api/protech/webhook` and nothing else, so the token the Beadle services share never leaves the
cluster; blank, the webhook takes the service token only. Keep it in an encrypted (`password2`) system property.
ProTech reaches the webhook through the Route of the `webhook-route` component of the OpenShift manifests, which
publishes `/api/protech/webhook` alone (see the README).

## What the service account needs in ProTech

Read on `change_request`, `change_task`, `sysapproval_approver` and the lookup tables; create and write on
`change_request` and `change_task`; write of `comments` on `sysapproval_approver`; the Table API (`rest_api_explorer`
or a role with web service access); time zone UTC and the default date and time formats (see above).

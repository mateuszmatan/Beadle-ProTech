package com.bbh.itss.dso.portal.adapter.out.servicenow;

import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.domain.change.ApprovalRef;
import com.bbh.itss.dso.portal.domain.change.ApprovalRole;
import com.bbh.itss.dso.portal.domain.change.ApprovalState;
import com.bbh.itss.dso.portal.domain.change.ChangeApproval;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeState;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import com.bbh.itss.dso.portal.domain.change.TaskState;
import com.bbh.itss.dso.portal.domain.change.WorkflowStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.change.ApprovalState.APPROVED;
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.REQUESTED;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.ESCALATED_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SUPPORT_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED;
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN;
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS;
import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now;
import static java.time.Duration.between;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Instant.MAX;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.ObjectUtils.max;

@Component
@RequiredArgsConstructor
class DemoServiceNowAdapter implements ServiceNowPort {

    static final Duration STAGE = ofMinutes(2);
    static final Duration SHORT_NOTICE = ofHours(24);
    static final Duration ESCALATED_LEAD = ofHours(2);
    static final String CLOSED_REFUSAL = "ProTech does not change a closed change";

    private final AtomicInteger changes = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));
    private final AtomicInteger tasks = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));
    private final Map<String, Held> held = new ConcurrentHashMap<>();
    private final List<SentReminder> reminders = new CopyOnWriteArrayList<>();
    private final Clock clock;
    private final DemoProTechProperties properties;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        String number = "CHG%07d".formatted(changes.incrementAndGet());
        Instant raisedAt = getIfNull(change.createdAt(), () -> now(clock));
        held.put(number, new Held(change.numbered(number, null), raisedAt, List.of(), List.of(), null, Map.of()));
        return new RaisedChange(number, null);
    }

    @Override
    public String createTask(String changeNumber, ChangeTask task) {
        return createTask(changeNumber, task, now(clock));
    }

    String createTask(String changeNumber, ChangeTask task, Instant at) {
        String number = nextTask();
        held.compute(changeNumber, (key, found) -> {
            Held current = open(key, found, at);
            return current.with(current.change().withTasks(List.of(task.numbered(number).in(OPEN))), at);
        });
        return number;
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        Instant now = now(clock);
        Map<String, ProductionChange> copies = new LinkedHashMap<>();
        for (ProductionChange change : known) {
            Held current = held.compute(change.number(), (number, found) ->
                    applied(getIfNull(found, () -> adopted(change, now)), now));
            copies.put(change.number(), current.copyFor(change, now));
        }
        return copies;
    }

    @Override
    public void update(ProductionChange change) {
        Instant now = now(clock);
        held.compute(change.number(), (number, found) -> open(number, found, now)
                .queued(new Queued(now.plus(properties.protechApplyDelay()), change)));
    }

    @Override
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        Instant now = now(clock);
        Held current = held.compute(changeNumber, (number, found) -> open(number, found, now));
        ProductionChange change = current.copyFor(current.change(), now);
        List<String> approvers = approval.role() == null ? approversOf(change, approval.task())
                : change.approvals().stream().filter(found -> found.role() == approval.role())
                .map(ChangeApproval::approver).filter(Objects::nonNull).toList();
        if (approvers.isEmpty()) {
            throw new IllegalStateException("ProTech has nobody to remind on " + changeNumber);
        }
        reminders.add(new SentReminder(changeNumber, approval, approvers, now));
        return approvers;
    }

    List<SentReminder> sent() {
        return List.copyOf(reminders);
    }

    static List<WorkflowStep> workflowOf(Instant raisedAt, ChangeSchedule schedule, Instant tasksApproved,
                                         Instant now) {
        Map<ChangeState, Instant> stages = new EnumMap<>(ChangeState.class);
        stages.put(DRAFT, raisedAt);
        stages.put(BUSINESS_APPROVAL, raisedAt.plus(STAGE));
        stages.put(PRIMARY_APPROVAL, raisedAt.plus(STAGE.multipliedBy(2)));
        stages.put(SECONDARY_APPROVAL, raisedAt.plus(STAGE.multipliedBy(3)));
        stages.put(SUPPORT_APPROVAL, raisedAt.plus(STAGE.multipliedBy(4)));
        stages.put(CTASK_APPROVAL, tasksRequested(raisedAt));
        if (tasksApproved.isBefore(MAX)) {
            Instant approved = max(raisedAt.plus(STAGE.multipliedBy(6)), tasksApproved);
            boolean shortNotice = between(approved, schedule.installationStart()).compareTo(SHORT_NOTICE) < 0;
            Instant implementation = shortNotice
                    ? max(approved.plus(STAGE), schedule.installationStart().minus(ESCALATED_LEAD)) : approved;
            if (shortNotice) {
                stages.put(ESCALATED_APPROVAL, approved);
            }
            stages.put(IMPLEMENTATION, implementation);
            stages.put(CLOSED, max(schedule.validationEnd(), implementation));
        }
        return stages.entrySet().stream().filter(stage -> !stage.getValue().isAfter(now))
                .map(stage -> new WorkflowStep(stage.getKey(), stage.getValue())).toList();
    }

    static Instant tasksRequested(Instant raisedAt) {
        return raisedAt.plus(STAGE.multipliedBy(5));
    }

    static TaskState taskStateOf(TaskState held, ChangeState state, boolean installing) {
        if (held == CANCELED) {
            return CANCELED;
        }
        if (state == CLOSED) {
            return TaskState.CLOSED;
        }
        return state == IMPLEMENTATION && installing ? WORK_IN_PROGRESS : OPEN;
    }

    static ApprovalState approvalOf(ChangeState state, ChangeState stage) {
        int reached = state.compareTo(stage);
        return reached < 0 ? NOT_APPROVED : reached == 0 ? REQUESTED : APPROVED;
    }

    static ApprovalState approvalAt(Instant now, Instant requested, Instant approved) {
        return now.isBefore(requested) ? NOT_APPROVED : now.isBefore(approved) ? REQUESTED : APPROVED;
    }

    private static List<String> approversOf(ProductionChange change, String task) {
        return change.tasks().stream().filter(found -> task.equals(found.number())).findFirst()
                .map(ChangeTask::approvers)
                .orElseThrow(() -> new IllegalStateException("ProTech has no change task " + task + " on "
                        + change.number()));
    }

    private Held open(String number, Held found, Instant now) {
        if (found == null) {
            throw new IllegalStateException("ProTech has no change " + number);
        }
        Held current = applied(found, now);
        if (current.stateAt(now) == CLOSED) {
            throw new IllegalStateException(CLOSED_REFUSAL);
        }
        return current;
    }

    private Held adopted(ProductionChange known, Instant now) {
        List<WorkflowStep> reached = known.workflow();
        Instant raisedAt = getIfNull(known.createdAt(), now);
        List<ChangeTask> numbered = numbered(known.tasks());
        return new Held(known.toBuilder().tasks(numbered).build(), raisedAt, List.of(), reached,
                reached.isEmpty() ? null : last(reached).enteredAt(),
                numbered.stream().collect(toMap(ChangeTask::number, task -> raisedAt, (first, second) -> first)));
    }

    private static WorkflowStep last(List<WorkflowStep> steps) {
        return steps.get(steps.size() - 1);
    }

    private List<ChangeTask> numbered(List<ChangeTask> changed) {
        return changed.stream().map(task -> task.number() == null ? task.numbered(nextTask()) : task).toList();
    }

    private String nextTask() {
        return "CTASK%07d".formatted(tasks.incrementAndGet());
    }

    private Held applied(Held found, Instant now) {
        Held current = found;
        List<Queued> waiting = new ArrayList<>();
        for (Queued update : found.queued()) {
            if (update.dueAt().isAfter(now)) {
                waiting.add(update);
            } else {
                current = current.with(apply(current.change(), update.requested(), update.dueAt()), update.dueAt());
            }
        }
        return current.waiting(waiting);
    }

    private ProductionChange apply(ProductionChange current, ProductionChange requested, Instant at) {
        boolean started = !at.isBefore(current.schedule().installationStart());
        Map<String, ChangeTask> heldTasks = current.tasks().stream().filter(task -> task.number() != null)
                .collect(toMap(ChangeTask::number, identity(), (first, second) -> first, LinkedHashMap::new));
        List<ChangeTask> changed = new ArrayList<>();
        for (ChangeTask task : requested.tasks()) {
            ChangeTask mine = heldTasks.remove(task.number());
            changed.add(mine == null ? task.numbered(null).in(OPEN)
                    : mine.state().frozen() ? mine : mine.editedTo(task));
        }
        List<ChangeTask> dropped = heldTasks.values().stream()
                .map(task -> task.state().frozen() ? task : task.in(CANCELED)).toList();
        return current.toBuilder().shortDescription(requested.shortDescription())
                .description(requested.description())
                .schedule(started ? current.schedule() : requested.schedule())
                .template(current.template().edited(requested.template()))
                .tasks(numbered(Stream.concat(changed.stream(), dropped.stream()).toList())).build();
    }

    record SentReminder(String changeNumber, ApprovalRef approval, List<String> sentTo, Instant at) {
    }

    private record Queued(Instant dueAt, ProductionChange requested) {
    }

    private record Held(ProductionChange change, Instant raisedAt, List<Queued> queued, List<WorkflowStep> kept,
                        Instant rescheduledAt, Map<String, Instant> tasked) {

        Held queued(Queued update) {
            return waiting(Stream.concat(queued.stream(), Stream.of(update)).toList());
        }

        Held waiting(List<Queued> waiting) {
            return new Held(change, raisedAt, waiting, kept, rescheduledAt, tasked);
        }

        Held with(ProductionChange applied, Instant at) {
            List<ChangeTask> added = applied.tasks().stream().filter(task -> !tasked.containsKey(task.number()))
                    .toList();
            Map<String, Instant> known = new HashMap<>(tasked);
            added.forEach(task -> known.put(task.number(), at));
            ChangeState state = stateAt(at);
            boolean reopened = added.stream().anyMatch(ChangeTask::active) && state.compareTo(CTASK_APPROVAL) > 0
                    && state != CLOSED;
            if (!reopened && applied.schedule().equals(change.schedule())) {
                return new Held(applied, raisedAt, queued, kept, rescheduledAt, known);
            }
            List<WorkflowStep> reached = workflowAt(at);
            return new Held(applied, raisedAt, queued, reopened
                    ? Stream.concat(reached.stream(), Stream.of(new WorkflowStep(CTASK_APPROVAL, at))).toList()
                    : reached, at, known);
        }

        Map<String, Instant> approvedAt() {
            Map<String, Instant> approved = new HashMap<>();
            List<ChangeTask> active = change.tasks().stream().filter(ChangeTask::active).toList();
            for (int index = 0; index < active.size(); index++) {
                ChangeTask task = active.get(index);
                approved.put(task.number(), requestedAt(task).plus(STAGE.multipliedBy(index + 1L)));
            }
            return approved;
        }

        Instant requestedAt(ChangeTask task) {
            return max(tasksRequested(raisedAt), tasked.getOrDefault(task.number(), raisedAt));
        }

        Instant tasksApproved() {
            return approvedAt().values().stream().max(Instant::compareTo).orElse(MAX);
        }

        List<WorkflowStep> workflowAt(Instant now) {
            Instant approved = tasksApproved();
            if (kept.isEmpty()) {
                return workflowOf(raisedAt, change.schedule(), approved, now);
            }
            ChangeState reached = last(kept).state();
            return Stream.concat(kept.stream(), workflowOf(raisedAt, change.schedule(), approved, MAX).stream()
                    .filter(step -> step.state().compareTo(reached) > 0)
                    .map(step -> new WorkflowStep(step.state(), max(step.enteredAt(), rescheduledAt)))
                    .filter(step -> !step.enteredAt().isAfter(now))).toList();
        }

        ChangeState stateAt(Instant now) {
            return last(workflowAt(now)).state();
        }

        ProductionChange copyFor(ProductionChange known, Instant now) {
            List<WorkflowStep> workflow = workflowAt(now);
            ChangeState state = last(workflow).state();
            boolean installing = !now.isBefore(change.schedule().installationStart());
            Map<String, Instant> approved = approvedAt();
            return known.toBuilder().shortDescription(change.shortDescription()).description(change.description())
                    .schedule(change.schedule()).template(change.template())
                    .tasks(change.tasks().stream().map(task -> task.withApproval(task.active()
                                    ? approvalAt(now, requestedAt(task), approved.get(task.number()))
                                    : task.approval(), DemoApprovers.of(task.details().assignmentGroup()))
                            .in(taskStateOf(task.state(), state, installing))).toList())
                    .url(change.url()).state(state).workflow(workflow)
                    .approvals(Stream.of(ApprovalRole.values()).map(role -> new ChangeApproval(role,
                            role.approverIn(change.template().approvers()), approvalOf(state, role.stage()),
                            null)).toList()).build();
        }
    }
}

package com.bbh.itss.dso.portal.adapter.out.servicenow;

import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeState;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import com.bbh.itss.dso.portal.domain.change.TaskState;
import com.bbh.itss.dso.portal.domain.change.WorkflowStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.ESCALATED_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED;
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN;
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS;
import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Instant.MAX;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.ObjectUtils.max;

@Component
class DemoServiceNowAdapter implements ServiceNowPort {

    static final Duration STAGE = ofMinutes(2);
    static final Duration SHORT_NOTICE = ofHours(24);
    static final Duration ESCALATED_LEAD = ofHours(2);
    static final String CLOSED_REFUSAL = "ProTech does not change a closed change";

    private final AtomicInteger changes = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));
    private final AtomicInteger tasks = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));
    private final Map<String, Held> held = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration applyDelay;

    DemoServiceNowAdapter(Clock clock, @Value("${dso.demo.protech-apply-delay:PT3S}") Duration applyDelay) {
        this.clock = clock;
        this.applyDelay = applyDelay;
    }

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        String number = "CHG%07d".formatted(changes.incrementAndGet());
        List<String> taskNumbers = change.tasks().stream().map(task -> nextTask()).toList();
        held.put(number, new Held(change.numbered(number, taskNumbers, null),
                getIfNull(change.createdAt(), () -> now(clock)), List.of(), List.of(), null));
        return new RaisedChange(number, taskNumbers, null);
    }

    @Override
    public Map<String, ProductionChange> read(Collection<ProductionChange> known) {
        Instant now = now(clock);
        Map<String, ProductionChange> copies = new LinkedHashMap<>();
        for (ProductionChange change : known) {
            Held current = held.compute(change.number(), (number, found) ->
                    applied(found == null ? adopted(change, now) : found, now));
            copies.put(change.number(), current.copyFor(change, now));
        }
        return copies;
    }

    @Override
    public void update(ProductionChange change) {
        Instant now = now(clock);
        held.compute(change.number(), (number, found) -> {
            if (found == null) {
                throw new IllegalStateException("ProTech has no change " + number);
            }
            Held current = applied(found, now);
            if (current.stateAt(now) == CLOSED) {
                throw new IllegalStateException(CLOSED_REFUSAL);
            }
            return current.queued(new Queued(now.plus(applyDelay), change));
        });
    }

    static List<WorkflowStep> workflowOf(Instant raisedAt, ChangeSchedule schedule, Instant now) {
        Instant approved = raisedAt.plus(STAGE.multipliedBy(5));
        boolean shortNotice = Duration.between(approved, schedule.installationStart()).compareTo(SHORT_NOTICE) < 0;
        Instant implementation = shortNotice
                ? max(approved.plus(STAGE), schedule.installationStart().minus(ESCALATED_LEAD)) : approved;
        Map<ChangeState, Instant> stages = new EnumMap<>(ChangeState.class);
        stages.put(DRAFT, raisedAt);
        stages.put(BUSINESS_APPROVAL, raisedAt.plus(STAGE));
        stages.put(PRIMARY_APPROVAL, raisedAt.plus(STAGE.multipliedBy(2)));
        stages.put(SECONDARY_APPROVAL, raisedAt.plus(STAGE.multipliedBy(3)));
        stages.put(CTASK_APPROVAL, raisedAt.plus(STAGE.multipliedBy(4)));
        if (shortNotice) {
            stages.put(ESCALATED_APPROVAL, approved);
        }
        stages.put(IMPLEMENTATION, implementation);
        stages.put(CLOSED, max(schedule.validationEnd(), implementation));
        return stages.entrySet().stream().filter(stage -> !stage.getValue().isAfter(now))
                .map(stage -> new WorkflowStep(stage.getKey(), stage.getValue())).toList();
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

    private Held adopted(ProductionChange known, Instant now) {
        return new Held(known.toBuilder().tasks(numbered(known.tasks())).build(),
                getIfNull(known.createdAt(), now), List.of(), List.of(), null);
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
            ChangeTask mine = task.number() == null ? null : heldTasks.remove(task.number());
            changed.add(mine == null ? task.numbered(null).in(OPEN)
                    : mine.state().frozen() ? mine : task.in(mine.state()));
        }
        List<ChangeTask> dropped = heldTasks.values().stream()
                .map(task -> task.state().frozen() ? task : task.in(CANCELED)).toList();
        return current.toBuilder().shortDescription(requested.shortDescription())
                .description(requested.description())
                .schedule(started ? current.schedule() : requested.schedule())
                .template(current.template().edited(requested.template()))
                .tasks(numbered(Stream.concat(changed.stream(), dropped.stream()).toList())).build();
    }

    private record Queued(Instant dueAt, ProductionChange requested) {
    }

    private record Held(ProductionChange change, Instant raisedAt, List<Queued> queued, List<WorkflowStep> kept,
                        Instant rescheduledAt) {

        Held queued(Queued update) {
            return waiting(Stream.concat(queued.stream(), Stream.of(update)).toList());
        }

        Held waiting(List<Queued> waiting) {
            return new Held(change, raisedAt, waiting, kept, rescheduledAt);
        }

        Held with(ProductionChange applied, Instant at) {
            return applied.schedule().equals(change.schedule())
                    ? new Held(applied, raisedAt, queued, kept, rescheduledAt)
                    : new Held(applied, raisedAt, queued, workflowAt(at), at);
        }

        List<WorkflowStep> workflowAt(Instant now) {
            if (kept.isEmpty()) {
                return workflowOf(raisedAt, change.schedule(), now);
            }
            ChangeState reached = kept.getLast().state();
            return Stream.concat(kept.stream(), workflowOf(raisedAt, change.schedule(), MAX).stream()
                    .filter(step -> step.state().compareTo(reached) > 0)
                    .map(step -> new WorkflowStep(step.state(), max(step.enteredAt(), rescheduledAt)))
                    .filter(step -> !step.enteredAt().isAfter(now))).toList();
        }

        ChangeState stateAt(Instant now) {
            return workflowAt(now).getLast().state();
        }

        ProductionChange copyFor(ProductionChange known, Instant now) {
            List<WorkflowStep> workflow = workflowAt(now);
            ChangeState state = workflow.getLast().state();
            boolean installing = !now.isBefore(change.schedule().installationStart());
            return known.toBuilder().shortDescription(change.shortDescription()).description(change.description())
                    .schedule(change.schedule()).template(change.template())
                    .tasks(change.tasks().stream()
                            .map(task -> task.in(taskStateOf(task.state(), state, installing))).toList())
                    .url(change.url()).state(state).workflow(workflow).build();
        }
    }
}

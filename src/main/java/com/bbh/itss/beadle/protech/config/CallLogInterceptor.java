package com.bbh.itss.beadle.protech.config;

import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.protech.application.calllog.CallerContext;
import com.bbh.itss.beadle.protech.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.protech.domain.calllog.Caller;
import com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.protech.domain.calllog.Outcome;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry.MESSAGE_MAX;
import static com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry.TARGET_MAX;
import static com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry.USER_MAX;
import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.GUI;
import static com.bbh.itss.beadle.protech.domain.calllog.ExternalSystem.PROTECH;
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.FAILURE;
import static com.bbh.itss.beadle.protech.domain.calllog.Outcome.SUCCESS;
import static com.bbh.itss.beadle.protech.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.util.ClassUtils.hasMethod;

/**
 * Writes a row of the call log around every call of a port to another system: who caused it, the correlation id,
 * the system, the operation (the name of the port method), its target (what the call was about, the number of what it
 * created first), the outcome with the message of a failure, and the duration. The row is written in a transaction
 * of its own and a call log that cannot be written never fails the call. Each call is logged too, under the
 * correlation id of its caller in the MDC: a read at DEBUG, a change at INFO and a failure at WARN. A look at the
 * activity of the changes that found nothing is left out of the call log, so that the ticks of the watch do not flood
 * it; a failed look is written.
 */
@Slf4j
@RequiredArgsConstructor
class CallLogInterceptor implements MethodInterceptor {

    static final Set<String> UNLOGGED = Set.of("mode");
    /** The calls that only read: logged at DEBUG, every other call changes something and is logged at INFO. */
    static final Set<String> READS = Set.of("read", "find", "activity");
    static final String MDC_KEY = "correlationId";
    static final String ACTIVITY = "activity";
    static final List<Class<?>> PORTS = List.of(ProTechPort.class, ProTechLookupPort.class, CallbackPort.class);

    private final ObjectProvider<CallLogPort> callLog;
    private final ObjectProvider<Clock> clock;

    static Pointcut pointcut() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return !UNLOGGED.contains(method.getName()) && PORTS.stream().anyMatch(port ->
                        port.isAssignableFrom(targetClass)
                                && hasMethod(port, method.getName(), method.getParameterTypes()));
            }
        };
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        ExternalSystem system = invocation.getThis() instanceof CallbackPort ? GUI : PROTECH;
        Instant at = now(clock.getIfAvailable(Clock::systemUTC));
        long started = System.nanoTime();
        String before = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, CallerContext.current().correlationId());
        try {
            Object result = invocation.proceed();
            String target = targetOf(invocation.getArguments(), result);
            long elapsed = elapsedSince(started);
            if (READS.contains(method.getName())) {
                log.debug("{} {} {} answered in {} ms", system, method.getName(), target, elapsed);
            } else {
                log.info("{} {} {} done in {} ms", system, method.getName(), target, elapsed);
            }
            if (!(ACTIVITY.equals(method.getName()) && result instanceof Map<?, ?> found && found.isEmpty())) {
                record(system, method, target, at, elapsed, SUCCESS, null);
            }
            return result;
        } catch (Throwable e) {
            String target = targetOf(invocation.getArguments(), null);
            String message = defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            long elapsed = elapsedSince(started);
            log.warn("{} {} {} failed after {} ms: {}", system, method.getName(), target, elapsed, message);
            record(system, method, target, at, elapsed, FAILURE, message);
            throw e;
        } finally {
            if (before == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, before);
            }
        }
    }

    private void record(ExternalSystem system, Method method, String target, Instant at, long elapsed,
                        Outcome outcome, String message) {
        Caller caller = CallerContext.current();
        CallLogEntry entry = new CallLogEntry(null, at, abbreviateBytes(caller.user(), USER_MAX),
                caller.correlationId(), system, method.getName(), target, outcome,
                abbreviateBytes(message, MESSAGE_MAX), elapsed);
        try {
            callLog.getObject().record(entry);
        } catch (RuntimeException e) {
            log.warn("The call of {}.{} on {} could not be written to the call log: {}", system, method.getName(),
                    entry.target(), e.getMessage());
        }
    }

    private static long elapsedSince(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    /** What the call was about: the number of what it created, then its arguments. */
    static String targetOf(Object[] arguments, Object result) {
        String created = switch (result) {
            case RaisedChange raised -> raised.number();
            case String number -> number;
            case null, default -> null;
        };
        String target = Stream.concat(Stream.of(created), Stream.of(arguments)).filter(Objects::nonNull)
                .map(CallLogInterceptor::describe).filter(StringUtils::isNotBlank).collect(joining(" "));
        return abbreviateBytes(trimToNull(target), TARGET_MAX);
    }

    private static String describe(Object argument) {
        return switch (argument) {
            case ProductionChange change -> change.number() != null ? change.number() : change.shortDescription();
            case ChangeTask task -> task.number() != null ? task.number()
                    : task.details() == null ? null : task.details().shortDescription();
            case ApprovalRef approval -> approval.role() != null ? approval.role().name() : approval.task();
            case ChangeCallback callback -> callback.number() + " " + callback.event();
            case LookupKind kind -> kind.path();
            case Collection<?> values -> values.stream().filter(Objects::nonNull).map(CallLogInterceptor::describe)
                    .filter(StringUtils::isNotBlank).collect(joining(","));
            default -> String.valueOf(argument);
        };
    }
}

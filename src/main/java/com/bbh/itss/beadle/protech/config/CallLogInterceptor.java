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
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry.CORRELATION_MAX;
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
 * of its own and a call log that cannot be written never fails the call. A look at the activity of the changes that
 * found nothing is left out, so that the ticks of the watch do not flood the log; a failed one is written.
 */
@Slf4j
@RequiredArgsConstructor
class CallLogInterceptor implements MethodInterceptor {

    static final Set<String> UNLOGGED = Set.of("connected", "mode", "pollEvery");
    static final String QUIET = "activity";
    static final Map<Class<?>, ExternalSystem> SYSTEMS = Map.of(ProTechPort.class, PROTECH,
            ProTechLookupPort.class, PROTECH, CallbackPort.class, GUI);

    private final ObjectProvider<CallLogPort> callLog;
    private final ObjectProvider<Clock> clock;

    static Pointcut pointcut() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return systemOf(method, targetClass).isPresent();
            }
        };
    }

    static Optional<ExternalSystem> systemOf(Method method, Class<?> targetClass) {
        if (UNLOGGED.contains(method.getName())) {
            return Optional.empty();
        }
        return SYSTEMS.entrySet().stream()
                .filter(port -> port.getKey().isAssignableFrom(targetClass)
                        && hasMethod(port.getKey(), method.getName(), method.getParameterTypes()))
                .map(Map.Entry::getValue).findFirst();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis() == null ? method.getDeclaringClass() : invocation.getThis().getClass();
        Optional<ExternalSystem> system = systemOf(method, targetClass);
        if (system.isEmpty()) {
            return invocation.proceed();
        }
        Instant at = now(clock.getIfAvailable(Clock::systemUTC));
        long started = System.nanoTime();
        try {
            Object result = invocation.proceed();
            if (!(QUIET.equals(method.getName()) && result instanceof Map<?, ?> found && found.isEmpty())) {
                record(system.get(), method, targetOf(invocation.getArguments(), result), at, started, SUCCESS, null);
            }
            return result;
        } catch (Throwable e) {
            record(system.get(), method, targetOf(invocation.getArguments(), null), at, started, FAILURE,
                    defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            throw e;
        }
    }

    private void record(ExternalSystem system, Method method, String target, Instant at, long started,
                        Outcome outcome, String message) {
        Caller caller = CallerContext.current();
        CallLogEntry entry = new CallLogEntry(null, at, abbreviateBytes(caller.user(), USER_MAX),
                abbreviateBytes(caller.correlationId(), CORRELATION_MAX), system, method.getName(), target, outcome,
                abbreviateBytes(message, MESSAGE_MAX), (System.nanoTime() - started) / 1_000_000);
        try {
            callLog.getObject().record(entry);
        } catch (RuntimeException e) {
            log.warn("The call of {}.{} on {} could not be written to the call log: {}", system, method.getName(),
                    entry.target(), e.getMessage());
        }
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

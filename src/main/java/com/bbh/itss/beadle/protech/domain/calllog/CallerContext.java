package com.bbh.itss.beadle.protech.domain.calllog;

import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * The caller of the work on the current thread: the user and correlation id of the call of Beadle-GUI being served,
 * or {@link Caller#SYSTEM} for the scheduled work. The correlation id is also in the logging MDC as
 * {@value #MDC_KEY}, so that every line logged for the work carries it.
 */
@NoArgsConstructor(access = PRIVATE)
public final class CallerContext {

    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    public static Caller current() {
        return getIfNull(CURRENT.get(), Caller.NONE);
    }

    /** Makes {@code caller} the current one until the scope is closed, then the one before. */
    public static Scope open(Caller caller) {
        Caller before = CURRENT.get();
        set(caller);
        return () -> set(before);
    }

    public static void run(Caller caller, Runnable work) {
        try (Scope scope = open(caller)) {
            work.run();
        }
    }

    public static <T> T as(Caller caller, Supplier<T> work) {
        try (Scope scope = open(caller)) {
            return work.get();
        }
    }

    private static void set(Caller caller) {
        if (caller == null) {
            CURRENT.remove();
            MDC.remove(MDC_KEY);
        } else {
            CURRENT.set(caller);
            MDC.put(MDC_KEY, caller.correlationId());
        }
    }

    /** The time a caller is the current one; closing it brings back the caller before. */
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}

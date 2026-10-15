package com.bbh.itss.beadle.protech.application.calllog;

import com.bbh.itss.beadle.protech.domain.calllog.Caller;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * The caller of the work on the current thread: the user and correlation id of the call of Beadle-GUI being served
 * ({@code X-Beadle-User}, {@code X-Correlation-Id}), or {@code system} for the scheduled work.
 */
@NoArgsConstructor(access = PRIVATE)
public final class CallerContext {

    public static final String USER_HEADER = "X-Beadle-User";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String SYSTEM_USER = "system";

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    public static Caller current() {
        return getIfNull(CURRENT.get(), Caller.NONE);
    }

    public static void set(Caller caller) {
        CURRENT.set(caller);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void run(Caller caller, Runnable work) {
        as(caller, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T as(Caller caller, Supplier<T> work) {
        Caller before = CURRENT.get();
        CURRENT.set(caller);
        try {
            return work.get();
        } finally {
            if (before == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(before);
            }
        }
    }
}

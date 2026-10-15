package com.bbh.itss.beadle.protech.application.change;

import com.bbh.itss.beadle.protech.application.UseCase;
import com.bbh.itss.beadle.protech.application.WithoutTransaction;
import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.protech.application.calllog.CallerContext;
import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.application.change.port.out.TrackedChangePort;
import com.bbh.itss.beadle.protech.domain.calllog.Caller;
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity;
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback;
import com.bbh.itss.beadle.protech.domain.change.TrackedChange;
import com.bbh.itss.beadle.protech.domain.change.WatchReport;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.bbh.itss.beadle.protech.application.calllog.CallerContext.SYSTEM_USER;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Watches the changes Beadle-GUI knows for what ProTech does to them. Each tick looks at ProTech once its poll
 * interval has passed (the demo ProTech at every tick, the live one every {@code beadle.protech.poll}), asks for what
 * moved since the earliest last look minus {@link #MARGIN}, and sends Beadle-GUI one callback per change that moved
 * past what it knows, under the correlation id of the user action that last touched the change. A change is marked
 * reported only once Beadle-GUI took its callback, so a failed callback is sent again at the next look.
 */
@UseCase
@RequiredArgsConstructor
public class ChangeWatchService implements ChangeWatchUseCase {

    /** How far before the last look a poll reaches back, for the clocks of ProTech and Beadle to disagree a little. */
    static final Duration MARGIN = Duration.ofMinutes(1);

    private final TrackedChangePort tracked;
    private final ProTechPort proTech;
    private final CallbackPort callbacks;
    private final Clock clock;
    private Instant lookedAt;

    @Override
    @WithoutTransaction
    public synchronized WatchReport tick() {
        Instant now = now(clock);
        if (lookedAt != null && now.isBefore(lookedAt.plus(proTech.pollEvery()))) {
            return WatchReport.NOTHING;
        }
        lookedAt = now;
        List<TrackedChange> watched = tracked.watched();
        if (watched.isEmpty()) {
            return WatchReport.NOTHING;
        }
        Instant since = watched.stream().map(change -> getIfNull(change.lastSeenAt(), change.lastEventAt()))
                .filter(Objects::nonNull).min(Instant::compareTo).orElse(now).minus(MARGIN);
        Map<String, ChangeActivity> activity = CallerContext.as(new Caller(SYSTEM_USER, randomUUID().toString()),
                () -> proTech.activity(watched.stream().map(TrackedChange::number).toList(), since));
        List<ChangeCallback> sent = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        List<String> quiet = new ArrayList<>();
        for (TrackedChange change : watched) {
            ChangeActivity found = activity.get(change.number());
            Optional<ChangeCallback> callback = found == null ? Optional.empty() : change.callbackFor(found, now);
            if (callback.isEmpty()) {
                quiet.add(change.number());
                continue;
            }
            try {
                CallerContext.run(new Caller(SYSTEM_USER, change.correlationId()),
                        () -> callbacks.notify(callback.get()));
                tracked.save(change.reported(found, now));
                sent.add(callback.get());
            } catch (RuntimeException e) {
                failures.put(change.number(), defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
        }
        if (!quiet.isEmpty()) {
            tracked.seen(quiet, now);
        }
        return new WatchReport(List.copyOf(sent), Collections.unmodifiableMap(failures));
    }
}

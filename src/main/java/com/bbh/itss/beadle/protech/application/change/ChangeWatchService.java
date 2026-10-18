package com.bbh.itss.beadle.protech.application.change;

import com.bbh.itss.beadle.protech.application.UseCase;
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
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException;
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

import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.beadle.protech.domain.shared.Timestamps.now;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Watches the changes Beadle-GUI knows for what ProTech does to them: every {@code beadle.protech.poll} the ticker looks
 * at all of them, and ProTech's webhook has one change looked at at once. A look asks for what moved since the
 * earliest last look minus {@link #MARGIN} and sends Beadle-GUI one callback per change that moved past what it
 * knows, under the correlation id of the user action that last touched the change. A change is marked reported only
 * once Beadle-GUI took its callback, so a failed callback is sent again at the next look; a tick and a webhook look at
 * once at worst tell Beadle-GUI twice, which it answers by reading the change again. The report is merged over the
 * change as it is written down by then, which keeps the read with which Beadle-GUI answers the callback and any action
 * of Beadle-GUI noted since the look.
 */
@UseCase
@RequiredArgsConstructor
public class ChangeWatchService implements ChangeWatchUseCase {

    /**
     * How far before the last look a poll reaches back, so that no update is missed between two looks. It widens the
     * window of the poll only: whether a move is news to Beadle-GUI compares ProTech's {@code sys_updated_on} with
     * times of Beadle-ProTech's clock, which needs the two clocks synchronised.
     */
    static final Duration MARGIN = Duration.ofMinutes(1);

    private final TrackedChangePort tracked;
    private final ProTechPort proTech;
    private final CallbackPort callbacks;
    private final Clock clock;

    @Override
    public WatchReport tick() {
        return lookAt(tracked.watched(), now(clock));
    }

    @Override
    public WatchReport look(String number) {
        String wanted = trimToNull(number);
        if (wanted == null) {
            throw InvalidRequestException.of("number", REQUIRED);
        }
        return lookAt(tracked.find(wanted).filter(TrackedChange::watched).stream().toList(), now(clock));
    }

    private WatchReport lookAt(List<TrackedChange> watched, Instant now) {
        if (watched.isEmpty()) {
            return WatchReport.NOTHING;
        }
        Instant since = watched.stream().map(change -> getIfNull(change.lastSeenAt(), change.lastEventAt()))
                .filter(Objects::nonNull).min(Instant::compareTo).orElse(now).minus(MARGIN);
        Map<String, ChangeActivity> activity = CallerContext.as(Caller.system(randomUUID().toString()),
                () -> proTech.activity(watched, since));
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
                CallerContext.run(Caller.system(change.correlationId()),
                        () -> callbacks.notify(callback.get()));
                tracked.reported(change, found, now);
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

package com.bbh.itss.beadle.protech.adapter.in.schedule;

import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase;
import com.bbh.itss.beadle.protech.domain.change.WatchReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks every {@value #DEFAULT_TICK} (or {@code beadle.protech.tick}) whether ProTech moved a change Beadle-GUI
 * knows: the demo ProTech at every tick, the live one once {@code beadle.protech.poll} has passed. A tick never
 * throws: what failed is logged and tried again on the next look.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeTicker {

    public static final String DEFAULT_TICK = "PT10S";
    static final String TICK = "${beadle.protech.tick:" + DEFAULT_TICK + "}";

    private final ChangeWatchUseCase watch;

    @Scheduled(fixedDelayString = TICK, initialDelayString = TICK)
    public void tick() {
        try {
            WatchReport report = watch.tick();
            report.sent().forEach(callback -> log.info("Told Beadle-GUI that ProTech change {} moved: {}, now {}",
                    callback.number(), callback.event(), callback.state()));
            report.failures().forEach((number, reason) -> log.warn(
                    "Beadle-GUI could not be told that change {} moved; it is told again at the next look: {}",
                    number, reason));
        } catch (RuntimeException e) {
            log.warn("The look at the changes in ProTech failed and is tried again on the next tick: {}",
                    e.getMessage(), e);
        }
    }
}

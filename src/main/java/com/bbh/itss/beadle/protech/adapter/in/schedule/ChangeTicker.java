package com.bbh.itss.beadle.protech.adapter.in.schedule;

import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks every {@code beadle.protech.poll} whether ProTech moved a change Beadle-GUI knows. A tick never throws: each
 * failed call is logged by the call log interceptor, and the look is tried again at the next poll.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeTicker {

    static final String POLL = "${beadle.protech.poll}";

    private final ChangeWatchUseCase watch;

    @Scheduled(fixedDelayString = POLL, initialDelayString = POLL)
    public void tick() {
        try {
            watch.tick();
        } catch (RuntimeException e) {
            log.warn("The look at the changes in ProTech failed and is tried again at the next poll: {}",
                    e.getMessage());
        }
    }
}

package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.application.change.port.in.ChangesUseCase;
import com.bbh.itss.beadle.protech.domain.change.ProTechStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protech")
@RequiredArgsConstructor
public class StatusController {

    private final ChangesUseCase changes;

    /** Whether ProTech is reached and which one: {@code {"connected": false, "mode": "demo"}}. */
    @GetMapping("/status")
    public StatusResponse status() {
        return StatusResponse.of(changes.status());
    }

    public record StatusResponse(boolean connected, String mode) {

        static StatusResponse of(ProTechStatus status) {
            return new StatusResponse(status.connected(), status.mode().label());
        }
    }
}

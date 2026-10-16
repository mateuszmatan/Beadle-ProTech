package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.application.change.port.in.ChangeWatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.ACCEPTED;

/**
 * The webhook ProTech calls when it moved a change: a business rule on {@code change_request}, {@code change_task}
 * and {@code sysapproval_approver} posts {@code {"number": "CHG0001234"}}, the number of the change, with the service
 * token. Beadle-ProTech looks at that change at once instead of at the next poll and calls Beadle-GUI back if it moved
 * past what Beadle-GUI knows; a change it does not follow is left alone. The answer is {@code 202} either way.
 */
@RestController
@RequestMapping("/api/protech/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ChangeWatchUseCase watch;

    @PostMapping
    @ResponseStatus(ACCEPTED)
    public void moved(@RequestBody MovedChange moved) {
        watch.look(moved.number());
    }

    public record MovedChange(String number) {
    }
}

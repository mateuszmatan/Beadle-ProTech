package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.application.change.port.in.ChangesUseCase;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.CreatedChange;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

/**
 * The production changes of ProTech as Beadle-GUI creates, reads, updates and reminds them; the bodies are the JSON of
 * {@link ProductionChange} and {@link ChangeTask} that docs/CONTRACTS.md calls {@code Change} and {@code ChangeTask}.
 */
@RestController
@RequestMapping("/api/protech/changes")
@RequiredArgsConstructor
public class ChangeController {

    private final ChangesUseCase changes;

    @PostMapping
    @ResponseStatus(CREATED)
    public CreatedChange create(@RequestBody ProductionChange draft) {
        return changes.create(draft);
    }

    @PostMapping("/{number}/tasks")
    @ResponseStatus(CREATED)
    public CreatedTask createTask(@PathVariable String number, @RequestBody ChangeTask task) {
        return new CreatedTask(changes.createTask(number, task));
    }

    @PostMapping("/read")
    public Map<String, ProductionChange> read(@RequestBody List<ProductionChange> known) {
        return changes.read(known);
    }

    @PutMapping("/{number}")
    @ResponseStatus(NO_CONTENT)
    public void update(@PathVariable String number, @RequestBody ProductionChange change) {
        changes.update(number, change);
    }

    @PostMapping("/{number}/reminders")
    public List<String> remind(@PathVariable String number, @RequestBody ApprovalRef approval) {
        return changes.remind(number, approval);
    }

    public record CreatedTask(String number) {
    }
}

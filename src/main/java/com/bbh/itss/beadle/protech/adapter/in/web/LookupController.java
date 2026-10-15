package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.application.change.port.in.LookupsUseCase;
import com.bbh.itss.beadle.protech.domain.change.Lookup;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The records of ProTech that fill the fields of a change, found by a text: {@code users}, {@code releases}, ... */
@RestController
@RequestMapping("/api/protech/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final LookupsUseCase lookups;

    @GetMapping("/{kind}")
    public List<Lookup> find(@PathVariable String kind, @RequestParam(required = false) String q,
                             @RequestParam(required = false) Integer limit) {
        return lookups.find(kind, q, limit);
    }
}

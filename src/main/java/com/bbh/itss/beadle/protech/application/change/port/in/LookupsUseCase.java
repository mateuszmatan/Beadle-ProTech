package com.bbh.itss.beadle.protech.application.change.port.in;

import com.bbh.itss.beadle.protech.domain.change.Lookup;

import java.util.List;

/** The records of ProTech that fill the fields of a change, found by a text. */
public interface LookupsUseCase {

    /** {@code kind} is the path of a {@link com.bbh.itss.beadle.protech.domain.change.LookupKind}; {@code limit} defaults to 20. */
    List<Lookup> find(String kind, String query, Integer limit);
}

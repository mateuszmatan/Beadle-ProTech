package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.ProTechMode;

import java.util.List;

/** The records of ProTech a change refers to: people, departments, groups, releases, items, incidents, problems, clients. */
public interface ProTechLookupPort {

    boolean connected();

    ProTechMode mode();

    List<Lookup> find(LookupKind kind, String query, int limit);
}

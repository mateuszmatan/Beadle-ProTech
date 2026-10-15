package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;

import java.util.List;

public interface ProTechLookupPort {

    List<Lookup> find(LookupKind kind, String query, int limit);
}

package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.Lookup;
import com.bbh.itss.dso.portal.domain.change.LookupKind;

import java.util.List;

public interface ProTechLookupPort {

    List<Lookup> find(LookupKind kind, String query, int limit);
}

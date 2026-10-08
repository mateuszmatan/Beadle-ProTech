package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ServiceNowPort {

    boolean connected();

    RaisedChange raise(ProductionChange change);

    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    record RaisedChange(String number, List<String> taskNumbers, String url) {
    }
}

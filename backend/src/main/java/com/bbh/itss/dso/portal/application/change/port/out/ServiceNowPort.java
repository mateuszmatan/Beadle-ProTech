package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.List;

public interface ServiceNowPort {

    boolean connected();

    RaisedChange raise(ProductionChange change);

    record RaisedChange(String number, List<String> taskNumbers, String url) {
    }
}

package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.ApprovalRef;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ServiceNowPort {

    boolean connected();

    RaisedChange raise(ProductionChange change);

    String createTask(String changeNumber, ChangeTask task);

    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    List<String> remind(String changeNumber, ApprovalRef approval);

    record RaisedChange(String number, String url) {
    }
}

package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProTechPort {

    boolean connected();

    RaisedChange raise(ProductionChange change);

    String createTask(String changeNumber, ChangeTask task);

    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    List<String> remind(String changeNumber, ApprovalRef approval);

    record RaisedChange(String number, String url) {
    }
}

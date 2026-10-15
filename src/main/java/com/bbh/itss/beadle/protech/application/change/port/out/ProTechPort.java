package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeActivity;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ProTechMode;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The production changes of ProTech: raised, read back, updated and their approvers reminded. */
public interface ProTechPort {

    boolean connected();

    ProTechMode mode();

    RaisedChange raise(ProductionChange change);

    String createTask(String changeNumber, ChangeTask task);

    /** The changes as ProTech holds them now, by number; a change ProTech does not know is left out. */
    Map<String, ProductionChange> read(Collection<ProductionChange> known);

    void update(ProductionChange change);

    /** Reminds the approver of a role or the approvers of a change task and answers whom it reminded. */
    List<String> remind(String changeNumber, ApprovalRef approval);

    /** How long to wait between two looks at the {@link #activity} of the changes: the poll of ProTech. */
    Duration pollEvery();

    /**
     * What ProTech did to the changes since {@code since}, by number; a change ProTech did nothing to, or does not
     * know, may be left out.
     */
    Map<String, ChangeActivity> activity(Collection<String> numbers, Instant since);
}

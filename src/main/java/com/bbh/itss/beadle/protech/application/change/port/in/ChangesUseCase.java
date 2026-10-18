package com.bbh.itss.beadle.protech.application.change.port.in;

import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ProTechStatus;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.CreatedChange;

import java.util.List;
import java.util.Map;

/** What Beadle-GUI does with the production changes in ProTech. */
public interface ChangesUseCase {

    ProTechStatus status();

    /** Creates the draft in ProTech and answers its number there. */
    CreatedChange create(ProductionChange draft);

    /** Creates the task on the change and answers its number in ProTech. */
    String createTask(String changeNumber, ChangeTask task);

    /** The current state of the known changes as ProTech holds them, by number. */
    Map<String, ProductionChange> read(List<ProductionChange> known);

    /** Sends the edited change to ProTech; the number of the path names the change the body must match. */
    void update(String number, ProductionChange change);

    /** Reminds the approver of a role or the approvers of a task, exactly one of the two, and answers whom. */
    List<String> remind(String changeNumber, ApprovalRef approval);
}

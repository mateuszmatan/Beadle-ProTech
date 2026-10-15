package com.bbh.itss.beadle.protech.application.change;

import com.bbh.itss.beadle.protech.application.UseCase;
import com.bbh.itss.beadle.protech.application.WithoutTransaction;
import com.bbh.itss.beadle.protech.application.change.port.in.ChangesUseCase;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.protech.domain.change.ApprovalRef;
import com.bbh.itss.beadle.protech.domain.change.ChangeTask;
import com.bbh.itss.beadle.protech.domain.change.ProTechStatus;
import com.bbh.itss.beadle.protech.domain.change.ProductionChange;
import com.bbh.itss.beadle.protech.domain.change.RaisedChange;
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.protech.domain.change.ChangeSchedule.UNPLANNED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTask.validateTasks;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.REQUIRED;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.beadle.protech.domain.change.ProductionChange.checkTemplateAndSchedule;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Checks what Beadle-GUI sends before it reaches ProTech, so that a change ProTech would refuse is refused here
 * with the fields at fault, and passes the rest on. Nothing is stored: ProTech is the system of record of a change
 * and Beadle-GUI keeps its own copy.
 */
@UseCase
@RequiredArgsConstructor
public class ChangeService implements ChangesUseCase {

    static final String ONE_OF_ROLE_OR_TASK = "name either the role of the change approver or the change task";

    private final ProTechPort proTech;

    @Override
    @WithoutTransaction
    public ProTechStatus status() {
        return new ProTechStatus(proTech.connected(), proTech.mode());
    }

    @Override
    @WithoutTransaction
    public RaisedChange raise(ProductionChange draft) {
        ValidationProblems problems = new ValidationProblems();
        check(draft, problems);
        problems.throwIfAny();
        return proTech.raise(draft);
    }

    @Override
    @WithoutTransaction
    public String createTask(String changeNumber, ChangeTask task) {
        ValidationProblems problems = new ValidationProblems();
        task.validate(UNPLANNED, problems);
        problems.throwIfAny();
        return proTech.createTask(numbered(changeNumber), task);
    }

    @Override
    @WithoutTransaction
    public Map<String, ProductionChange> read(List<ProductionChange> known) {
        ValidationProblems problems = new ValidationProblems();
        for (int index = 0; index < known.size(); index++) {
            problems.require("[" + index + "].number", known.get(index).number(), REQUIRED);
        }
        problems.throwIfAny();
        return proTech.read(known);
    }

    @Override
    @WithoutTransaction
    public void update(String number, ProductionChange change) {
        ValidationProblems problems = new ValidationProblems();
        String path = numbered(number);
        if (change.number() != null && !change.number().equals(path)) {
            problems.add("number", "must be " + path + ", the change of the path");
        }
        check(change, problems);
        problems.throwIfAny();
        proTech.update(change.number() == null ? change.numbered(path, change.url()) : change);
    }

    @Override
    @WithoutTransaction
    public List<String> remind(String changeNumber, ApprovalRef approval) {
        if ((approval.role() == null) == (trimToNull(approval.task()) == null)) {
            throw InvalidRequestException.of("task", ONE_OF_ROLE_OR_TASK);
        }
        return proTech.remind(numbered(changeNumber), new ApprovalRef(approval.role(), trimToNull(approval.task())));
    }

    private static void check(ProductionChange change, ValidationProblems problems) {
        problems.require("shortDescription", trimToNull(change.shortDescription()), REQUIRED)
                .fits("shortDescription", change.shortDescription(), SHORT_DESCRIPTION_MAX)
                .require("description", trimToNull(change.description()), REQUIRED)
                .fits("description", change.description(), DESCRIPTION_MAX);
        checkTemplateAndSchedule(change.template(), change.schedule(), null, problems);
        validateTasks(change.tasks(), getIfNull(change.schedule(), UNPLANNED), problems);
    }

    private static String numbered(String changeNumber) {
        String number = trimToNull(changeNumber);
        if (number == null) {
            throw InvalidRequestException.of("number", REQUIRED);
        }
        return number;
    }
}

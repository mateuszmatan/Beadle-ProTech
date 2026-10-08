package com.bbh.itss.dso.portal.domain.change;

import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeTask(String number, String shortDescription, String description, TaskState state) {

    public ChangeTask {
        number = trimToNull(number);
        shortDescription = trimToNull(shortDescription);
        description = trimToNull(description);
        state = getIfNull(state, OPEN);
    }

    public static ChangeTask of(TaskText text) {
        return new ChangeTask(null, text.shortDescription(), text.description(), OPEN);
    }

    public TaskText text() {
        return new TaskText(shortDescription, description);
    }

    public ChangeTask numbered(String number) {
        return new ChangeTask(number, shortDescription, description, state);
    }

    public ChangeTask in(TaskState state) {
        return new ChangeTask(number, shortDescription, description, state);
    }
}

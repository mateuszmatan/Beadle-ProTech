package com.bbh.itss.dso.portal.domain.change;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;

public record Reminder(Instant sentAt, List<String> sentTo) {

    public static final int MAX_PEOPLE = 9;

    public Reminder {
        sentTo = people(sentTo);
    }

    public static List<String> people(List<String> names) {
        return clean(names).stream().map(name -> abbreviateBytes(name, GROUP_MAX)).limit(MAX_PEOPLE).toList();
    }
}

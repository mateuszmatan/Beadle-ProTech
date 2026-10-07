package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;

public record ChangeSchedule(Instant installationStart, Instant installationEnd, Instant validationStart,
                             Instant validationEnd, Instant firstUsage) {

    private static final DateTimeFormatter DAY_AND_TIME = ofPattern("yyyy-MM-dd HH:mm").withZone(UTC);
    private static final DateTimeFormatter TIME = ofPattern("HH:mm").withZone(UTC);

    public void check(ValidationProblems problems) {
        problems.require("installationStart", installationStart, "choose when the installation starts")
                .require("installationEnd", installationEnd, "choose when the installation ends")
                .require("validationStart", validationStart, "choose when the post-install validation starts")
                .require("validationEnd", validationEnd, "choose when the post-install validation ends")
                .require("firstUsage", firstUsage, "choose when the release is first used");
        if (allNotNull(installationStart, installationEnd) && !installationEnd.isAfter(installationStart)) {
            problems.add("installationEnd", "must be after the installation start");
        }
        notBefore(problems, "validationStart", validationStart, installationEnd, "the installation end");
        notBefore(problems, "validationEnd", validationEnd, validationStart, "the validation start");
        notBefore(problems, "firstUsage", firstUsage, validationEnd, "the validation end");
    }

    public void checkUpcoming(Instant now, ValidationProblems problems) {
        if (installationStart != null && !installationStart.isAfter(now)) {
            problems.add("installationStart", "must be in the future");
        }
    }

    public String installationText() {
        return text(installationStart, installationEnd);
    }

    public String text() {
        return "Installation " + installationText() + ", post-install validation "
                + text(validationStart, validationEnd) + ", first usage " + DAY_AND_TIME.format(firstUsage) + " UTC";
    }

    private static void notBefore(ValidationProblems problems, String field, Instant value, Instant earliest,
                                  String what) {
        if (allNotNull(value, earliest) && value.isBefore(earliest)) {
            problems.add(field, "must not be before " + what);
        }
    }

    private static String text(Instant start, Instant end) {
        boolean sameDay = DAY_AND_TIME.format(start).regionMatches(0, DAY_AND_TIME.format(end), 0, 10);
        return DAY_AND_TIME.format(start) + " to " + (sameDay ? TIME : DAY_AND_TIME).format(end) + " UTC";
    }
}

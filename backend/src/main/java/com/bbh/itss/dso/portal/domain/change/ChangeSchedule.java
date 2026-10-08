package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;

public record ChangeSchedule(Instant installationStart, Instant installationEnd, Instant validationStart,
                             Instant validationEnd, Instant firstUsage, Instant downtimeStart, Instant downtimeEnd) {

    public static final ChangeSchedule UNPLANNED = new ChangeSchedule(null, null, null, null, null, null, null);

    private static final DateTimeFormatter DAY_AND_TIME = ofPattern("yyyy-MM-dd HH:mm").withZone(UTC);
    private static final DateTimeFormatter TIME = ofPattern("HH:mm").withZone(UTC);
    private static final String NOT_PLANNED = "not planned yet";

    public void check(boolean downtime, ValidationProblems problems) {
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
        if (downtime) {
            problems.require("downtimeStart", downtimeStart, "choose when the downtime starts")
                    .require("downtimeEnd", downtimeEnd, "choose when the downtime ends");
            if (allNotNull(downtimeStart, downtimeEnd) && !downtimeEnd.isAfter(downtimeStart)) {
                problems.add("downtimeEnd", "must be after the downtime start");
            }
        } else {
            empty(problems, "downtimeStart", downtimeStart);
            empty(problems, "downtimeEnd", downtimeEnd);
        }
    }

    public void checkUpcoming(Instant now, ValidationProblems problems) {
        if (installationStart != null && !installationStart.isAfter(now)) {
            problems.add("installationStart", "must be in the future");
        }
    }

    public String text() {
        return "Installation " + text(installationStart, installationEnd) + ", post-install validation "
                + text(validationStart, validationEnd) + ", first usage " + text(firstUsage) + ". "
                + (allNotNull(downtimeStart, downtimeEnd) ? "Downtime " + text(downtimeStart, downtimeEnd) + "."
                : "No downtime.");
    }

    private static void notBefore(ValidationProblems problems, String field, Instant value, Instant earliest,
                                  String what) {
        if (allNotNull(value, earliest) && value.isBefore(earliest)) {
            problems.add(field, "must not be before " + what);
        }
    }

    private static void empty(ValidationProblems problems, String field, Instant value) {
        if (value != null) {
            problems.add(field, "must be empty without downtime");
        }
    }

    private static String text(Instant at) {
        return at == null ? NOT_PLANNED : DAY_AND_TIME.format(at) + " UTC";
    }

    private static String text(Instant start, Instant end) {
        if (!allNotNull(start, end)) {
            return NOT_PLANNED;
        }
        boolean sameDay = DAY_AND_TIME.format(start).regionMatches(0, DAY_AND_TIME.format(end), 0, 10);
        return DAY_AND_TIME.format(start) + " to " + (sameDay ? TIME : DAY_AND_TIME).format(end) + " UTC";
    }
}

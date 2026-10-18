package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;

import java.time.Instant;

import static org.apache.commons.lang3.ObjectUtils.allNotNull;

public record ChangeSchedule(Instant installationStart, Instant installationEnd, Instant validationStart,
                             Instant validationEnd, Instant firstUsage, Instant downtimeStart, Instant downtimeEnd) {

    public static final ChangeSchedule UNPLANNED = new ChangeSchedule(null, null, null, null, null, null, null);

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
}

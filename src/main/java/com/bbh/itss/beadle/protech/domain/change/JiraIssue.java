package com.bbh.itss.beadle.protech.domain.change;

import java.time.LocalDate;

public record JiraIssue(String key, String summary, String status, String epicKey, LocalDate updated) {

    public String line() {
        return key + " " + summary + (status == null ? "" : " (" + status + ")");
    }
}

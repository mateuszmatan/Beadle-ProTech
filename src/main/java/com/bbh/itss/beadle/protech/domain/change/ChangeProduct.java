package com.bbh.itss.beadle.protech.domain.change;

import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.jiraKeyOf;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

public record ChangeProduct(long id, String code, String name, String ownerTeam,
                            Long businessServiceId, String businessServiceName, String jiraProjectKey) {

    public String jiraProject() {
        return getIfNull(jiraProjectKey, () -> jiraKeyOf(code));
    }

    public Long departmentId() {
        return businessServiceId;
    }

    public String departmentName() {
        return businessServiceName;
    }
}

package com.bbh.itss.beadle.protech.adapter.out.persistence;

import com.bbh.itss.beadle.protech.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.DESC;

/** The call log in BEADLE_PROTECH_CALL_LOG; each entry is committed on its own, as the repository saves it. */
@Component
@RequiredArgsConstructor
class CallLogPersistenceAdapter implements CallLogPort {

    private final CallLogJpaRepository entries;

    @Override
    public CallLogEntry record(CallLogEntry entry) {
        return entries.save(CallLogEntity.of(entry)).toDomain();
    }

    @Override
    public CallLogPage find(CallLogQuery query) {
        Page<CallLogEntity> page = entries.findAll(matching(query),
                PageRequest.of(query.page(), query.size(), Sort.by(DESC, "at", "id")));
        return new CallLogPage(query.page(), query.size(), page.getTotalElements(),
                page.getContent().stream().map(CallLogEntity::toDomain).toList());
    }

    private static Specification<CallLogEntity> matching(CallLogQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.correlationId() != null) {
                predicates.add(builder.equal(root.get("correlationId"), query.correlationId()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("at"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get("at"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

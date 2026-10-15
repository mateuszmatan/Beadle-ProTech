package com.bbh.itss.beadle.protech.application.change;

import com.bbh.itss.beadle.protech.application.UseCase;
import com.bbh.itss.beadle.protech.application.WithoutTransaction;
import com.bbh.itss.beadle.protech.application.change.port.in.LookupsUseCase;
import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.shared.InvalidRequestException;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.beadle.protech.domain.change.Lookup.MAX_LOOKUPS;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@UseCase
@RequiredArgsConstructor
public class LookupService implements LookupsUseCase {

    public static final int LIMIT_MAX = 100;

    private final ProTechLookupPort lookups;

    @Override
    @WithoutTransaction
    public List<Lookup> find(String kind, String query, Integer limit) {
        int size = getIfNull(limit, MAX_LOOKUPS);
        if (size < 1 || size > LIMIT_MAX) {
            throw InvalidRequestException.of("limit", "must be between 1 and " + LIMIT_MAX);
        }
        return lookups.find(LookupKind.of(kind), trimToEmpty(query), size);
    }
}

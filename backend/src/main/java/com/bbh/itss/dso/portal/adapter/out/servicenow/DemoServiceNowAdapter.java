package com.bbh.itss.dso.portal.adapter.out.servicenow;

import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Component
class DemoServiceNowAdapter implements ServiceNowPort {

    private final AtomicInteger changes = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));
    private final AtomicInteger tasks = new AtomicInteger(1_000_000 + new SecureRandom().nextInt(8_000_000));

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public RaisedChange raise(ProductionChange change) {
        return new RaisedChange("CHG%07d".formatted(changes.incrementAndGet()),
                IntStream.range(0, change.tasks().size())
                        .mapToObj(index -> "CTASK%07d".formatted(tasks.incrementAndGet())).toList(), null);
    }
}

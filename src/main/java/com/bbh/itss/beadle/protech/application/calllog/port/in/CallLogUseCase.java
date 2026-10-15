package com.bbh.itss.beadle.protech.application.calllog.port.in;

import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery;

public interface CallLogUseCase {

    CallLogPage find(CallLogQuery query);
}

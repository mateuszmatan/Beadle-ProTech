package com.bbh.itss.beadle.protech.application.calllog;

import com.bbh.itss.beadle.protech.application.UseCase;
import com.bbh.itss.beadle.protech.application.calllog.port.in.CallLogUseCase;
import com.bbh.itss.beadle.protech.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class CallLogService implements CallLogUseCase {

    private final CallLogPort callLog;

    @Override
    public CallLogPage find(CallLogQuery query) {
        return callLog.find(query);
    }
}

package com.bbh.itss.beadle.protech.application.calllog.port.out;

import com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.protech.domain.calllog.CallLogQuery;

public interface CallLogPort {

    /** Writes the entry in a transaction of its own, so that it stays whatever happens to the work around it. */
    CallLogEntry record(CallLogEntry entry);

    /** The entries that match the query, newest first. */
    CallLogPage find(CallLogQuery query);
}

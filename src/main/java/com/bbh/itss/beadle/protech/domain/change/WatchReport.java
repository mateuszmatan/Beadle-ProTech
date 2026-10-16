package com.bbh.itss.beadle.protech.domain.change;

import java.util.List;
import java.util.Map;

/** What a tick of the watch did: the callbacks Beadle-GUI took, and why the others failed, by change number. */
public record WatchReport(List<ChangeCallback> sent, Map<String, String> failures) {

    public static final WatchReport NOTHING = new WatchReport(List.of(), Map.of());
}

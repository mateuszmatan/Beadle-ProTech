package com.bbh.itss.beadle.protech.domain.change;

import static java.util.Locale.ROOT;

/** Whether the service talks to the real ProTech or plays one by the clock. */
public enum ProTechMode {
    DEMO, LIVE;

    /** The mode as the API spells it: {@code demo} or {@code live}. */
    public String label() {
        return name().toLowerCase(ROOT);
    }
}

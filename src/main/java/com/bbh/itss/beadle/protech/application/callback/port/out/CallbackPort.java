package com.bbh.itss.beadle.protech.application.callback.port.out;

import com.bbh.itss.beadle.protech.domain.change.ChangeCallback;

/** Beadle-GUI, told that ProTech moved a change; a callback Beadle-GUI does not take is thrown. */
public interface CallbackPort {

    void notify(ChangeCallback callback);
}

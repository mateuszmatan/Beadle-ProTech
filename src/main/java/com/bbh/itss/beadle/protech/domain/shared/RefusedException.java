package com.bbh.itss.beadle.protech.domain.shared;

/**
 * A request ProTech refuses, or would refuse, for what it holds: a change or task it does not have, a closed change,
 * nobody left to remind, a {@code 4xx} of the Table API. The API answers it {@code 409} with this message; any other
 * {@link IllegalStateException} is a failure of Beadle-ProTech or its framework, answered {@code 500} without one.
 */
public class RefusedException extends IllegalStateException {

    public RefusedException(String message) {
        super(message);
    }

    public static RefusedException noChange(String number) {
        return new RefusedException("ProTech has no change " + number);
    }

    public static RefusedException noTask(String task, String change) {
        return new RefusedException("ProTech has no change task " + task + " on " + change);
    }

    public static RefusedException nobodyToRemind(String change) {
        return new RefusedException("ProTech has nobody to remind on " + change);
    }
}

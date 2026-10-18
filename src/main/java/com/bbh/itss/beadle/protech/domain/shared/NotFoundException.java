package com.bbh.itss.beadle.protech.domain.shared;

/**
 * Something the request names that Beadle-ProTech does not have, such as a lookup it does not offer. The API answers it
 * {@code 404} with this message; any other {@link java.util.NoSuchElementException} is a failure of Beadle-ProTech,
 * answered {@code 500} without one.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

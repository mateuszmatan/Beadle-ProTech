package com.bbh.itss.beadle.protech.domain.shared;

import lombok.NoArgsConstructor;

import java.util.NoSuchElementException;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Failures {

    public static NoSuchElementException notFound(String entity, Object id) {
        return new NoSuchElementException(entity + " " + id + " does not exist");
    }
}

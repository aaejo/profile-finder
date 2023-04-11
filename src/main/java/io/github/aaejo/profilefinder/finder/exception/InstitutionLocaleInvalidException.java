package io.github.aaejo.profilefinder.finder.exception;

import java.util.Locale;

import io.github.aaejo.messaging.records.Institution;

public class InstitutionLocaleInvalidException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Could not process website for %s. Locale was %s.";

    public InstitutionLocaleInvalidException(Institution institution, Locale locale) {
        super(String.format(MESSAGE_TEMPLATE, institution.name(), locale.getDisplayName()));
    }
}

package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.messaging.records.Institution;

public class FacultyListNotFoundException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Could not find faculty list for %s. Best candidate was %s with %.3f confidence.";

    public FacultyListNotFoundException(Institution institution, String candidate, double confidence) {
        super(String.format(MESSAGE_TEMPLATE, institution.name(), candidate, confidence));
    }
}

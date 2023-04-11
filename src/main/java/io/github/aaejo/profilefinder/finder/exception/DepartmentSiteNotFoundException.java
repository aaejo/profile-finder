package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.messaging.records.Institution;

public class DepartmentSiteNotFoundException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Could not find department site for %s. Best candidate was %s with %.3f confidence.";

    public DepartmentSiteNotFoundException(Institution institution, String candidate, double confidence) {
        super(String.format(MESSAGE_TEMPLATE, institution.name(), candidate, confidence));
    }
}

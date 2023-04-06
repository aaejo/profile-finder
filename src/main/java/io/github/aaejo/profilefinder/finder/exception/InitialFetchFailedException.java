package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.messaging.records.Institution;

public class InitialFetchFailedException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Initial page load failed for %s. Unable to get page from %s.";

    private Institution institution;

    public InitialFetchFailedException(Institution institution) {
        super(String.format(MESSAGE_TEMPLATE, institution.name(), institution.website()));

        this.institution = institution;
    }
}

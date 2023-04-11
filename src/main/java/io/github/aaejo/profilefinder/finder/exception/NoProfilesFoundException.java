package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.messaging.records.Institution;

public class NoProfilesFoundException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "No profiles extracted from %s for %s";

    public NoProfilesFoundException(Institution institution, String url) {
        super(String.format(MESSAGE_TEMPLATE, url, institution.name()));
    }

}

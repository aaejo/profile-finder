package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.finder.client.FinderClientResponse;
import io.github.aaejo.messaging.records.Institution;

public class InitialFetchFailedException extends RuntimeException {
    private static final String NO_RESPONSE_MESSAGE_TEMPLATE = "Initial page load failed for %s. Unable to get page from %s.";
    private static final String BAD_RESPONSE_MESSAGE_TEMPLATE = NO_RESPONSE_MESSAGE_TEMPLATE + " Response status was [%d]";

    public InitialFetchFailedException(Institution institution) {
        super(String.format(NO_RESPONSE_MESSAGE_TEMPLATE, institution.name(), institution.website()));
    }

    public InitialFetchFailedException(Institution institution, FinderClientResponse response) {
        super(response.status() != -1 ?
                String.format(BAD_RESPONSE_MESSAGE_TEMPLATE, institution.name(), institution.website(), response.status())
                : String.format(NO_RESPONSE_MESSAGE_TEMPLATE, institution.name(), institution.website()));

        if (response.exception().isPresent()) {
            this.initCause(response.exception().get());
        }
    }

    
}

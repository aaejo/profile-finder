package io.github.aaejo.profilefinder.finder.exception;

import io.github.aaejo.messaging.records.Institution;

public class FacultyListNotFoundException extends RuntimeException {
    private static final String MESSAGE_TEMPLATE = "Could not find faculty list for %s. Best candidate was %s with %.3f confidence.";

    private Institution institution;
    private String canditate;
    private double confidence;

    public FacultyListNotFoundException(Institution institution, String candidate, double confidence) {
        super(String.format(MESSAGE_TEMPLATE, institution.name(), candidate, confidence));

        this.institution = institution;
        this.canditate = candidate;
        this.confidence = confidence;
    }
}

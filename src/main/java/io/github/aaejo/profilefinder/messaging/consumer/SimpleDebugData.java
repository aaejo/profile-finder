package io.github.aaejo.profilefinder.messaging.consumer;

import io.github.aaejo.messaging.records.Institution;

public record SimpleDebugData(Institution institution, String url, double confidence) {
}

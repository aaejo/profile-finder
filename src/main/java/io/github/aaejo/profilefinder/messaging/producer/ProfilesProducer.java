package io.github.aaejo.profilefinder.messaging.producer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Profile;

/**
 * @author Omri Harary
 */
@Component
public class ProfilesProducer {
    private static final Logger log = LoggerFactory.getLogger(ProfilesProducer.class);
    private static final String TOPIC = "profiles";

    private final KafkaTemplate<String, Profile> template;

    public ProfilesProducer(KafkaTemplate<String, Profile> template) {
        this.template = template;
    }

    public void send(final Profile profile) {
        CompletableFuture<SendResult<String, Profile>> sendResultFuture = this.template.send(TOPIC, profile);
        sendResultFuture.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Sent: " + profile.toString());
            }
            else {
                log.error("Failed to send: " + profile.toString(), ex);
            }
        });
    }
}

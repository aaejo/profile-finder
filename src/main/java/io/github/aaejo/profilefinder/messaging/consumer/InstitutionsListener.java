package io.github.aaejo.profilefinder.messaging.consumer;

import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@KafkaListener(id = "profile-finder", topics = "institutions")
public class InstitutionsListener {

    @KafkaHandler
    public void handle(Institution institution) {
        log.info(institution.toString());
    }
    
}

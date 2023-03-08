package io.github.aaejo.profilefinder.messaging.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import io.github.aaejo.profilefinder.finder.exception.InstitutionLocaleInvalidException;

@Configuration
public class ConsumerConfiguration {

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        // Institutions that fail to process will be retried once after waiting for 2
        // seconds. If they fail again, they will be sent to a dead-letter topic.
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),
                new FixedBackOff(2000L, 1L));
        // Do not retry when the institution language cannot be handled
        handler.addNotRetryableExceptions(InstitutionLocaleInvalidException.class);

        return handler;
    }
}

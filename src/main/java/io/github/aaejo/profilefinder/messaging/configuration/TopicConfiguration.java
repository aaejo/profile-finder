package io.github.aaejo.profilefinder.messaging.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * @author Omri Harary
 */
@Configuration
public class TopicConfiguration {

    @Bean
    public NewTopic institutionsTopic() {
        return TopicBuilder
                .name("institutions")
                .build();
    }

    /**
     * Dead-letter topic for institutions that failed to process
     * i.e. when unable to find department/faculty/profile
     */
    @Bean
    public NewTopic institutionsDLT() {
        return TopicBuilder
                .name("institutions.DLT")
                .build();
    }

    @Bean
    public NewTopic profilesTopic() {
        return TopicBuilder
                .name("profiles")
                .build();
    }

    @Bean
    public NewTopic departmentDebugTopic() {
        return TopicBuilder
                .name("department.debug")
                .build();
    }

    @Bean
    public NewTopic facultyDebugTopic() {
        return TopicBuilder
                .name("faculty.debug")
                .build();
    }

    @Bean
    public NewTopic profilesDebugTopic() {
        return TopicBuilder
                .name("profiles.debug")
                .build();
    }
}

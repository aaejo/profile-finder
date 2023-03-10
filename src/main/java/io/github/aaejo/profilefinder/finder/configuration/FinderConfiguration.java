package io.github.aaejo.profilefinder.finder.configuration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import io.github.aaejo.finder.client.FinderClient;

@Configuration
public class FinderConfiguration {

    @Bean
    public FinderClient client(RetryTemplate retryTemplate) {
        // Any client settings that should apply to all Jsoup connections
        // can be applied here
        Connection session = Jsoup
                .newSession()
                .ignoreHttpErrors(true); // We want to be able to inspect HTTP errors ourselves

        return new FinderClient(session, retryTemplate);
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        FixedBackOffPolicy backoff = new FixedBackOffPolicy();
        backoff.setBackOffPeriod(2000L);
        template.setBackOffPolicy(backoff);

        SimpleRetryPolicy retry = new SimpleRetryPolicy(2);
        template.setRetryPolicy(retry);

        return template;
    }
}

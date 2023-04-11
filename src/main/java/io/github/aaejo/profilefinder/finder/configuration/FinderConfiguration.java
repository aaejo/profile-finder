package io.github.aaejo.profilefinder.finder.configuration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.finder.client.FinderClientProperties;

/**
 * @author Omri Harary
 */
@Configuration
@EnableConfigurationProperties({ CrawlingProperties.class, DepartmentFinderProperties.class })
public class FinderConfiguration {

    @Bean
    @ConfigurationProperties("aaejo.jds.client")
    public FinderClientProperties clientProperties() {
        return new FinderClientProperties();
    }

    @Bean
    public FinderClient client(FinderClientProperties clientProperties) {
        // Any client settings that should apply to all Jsoup connections
        // can be applied here
        Connection session = Jsoup
                .newSession()
                .ignoreHttpErrors(true); // We want to be able to inspect HTTP errors ourselves

        return new FinderClient(session, clientProperties);
    }
}

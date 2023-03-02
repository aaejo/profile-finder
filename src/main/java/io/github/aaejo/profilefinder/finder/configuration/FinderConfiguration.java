package io.github.aaejo.profilefinder.finder.configuration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinderConfiguration {

    @Bean
    public Connection session() {
        // Any client settings that should apply to all Jsoup connections
        // can be applied here
        return Jsoup.newSession();
    }
}

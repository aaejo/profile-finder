package io.github.aaejo.profilefinder.finder.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Omri Harary
 */
@ConfigurationProperties("aaejo.jds.finder.crawling")
public record CrawlingProperties(boolean offHostCrawlingAllowed, double offHostCrawlingWeight, String[] disallowedHosts) {
}

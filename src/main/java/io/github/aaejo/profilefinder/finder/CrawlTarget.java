package io.github.aaejo.profilefinder.finder;

import java.util.Comparator;

/**
 * @author Omri Harary
 */
public record CrawlTarget(String url, double weight, SearchState source) implements Comparable<CrawlTarget> {

    @Override
    public int compareTo(CrawlTarget o) {
        return Comparator.comparingDouble(CrawlTarget::weight).compare(this, o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CrawlTarget)) {
            return false;
        } else {
            CrawlTarget other = (CrawlTarget) obj;
            return url.equals(other.url);
        }
    }
}

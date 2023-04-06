package io.github.aaejo.profilefinder.finder;

import java.util.Comparator;
import java.util.PriorityQueue;

import org.jsoup.select.Elements;

/**
 * @author Omri Harary
 */
public class CrawlQueue extends PriorityQueue<CrawlTarget> {

    /**
     * 
     */
    public CrawlQueue() {
        super(Comparator.reverseOrder());
    }

    /**
     * @param initialCapacity
     */
    public CrawlQueue(int initialCapacity) {
        super(initialCapacity, Comparator.reverseOrder());
    }

    @Override
    public boolean offer(CrawlTarget e) {
        // The queue should only ever contain unique target URLs
        if (contains(e)) {
            // If the queue contains a target URL, remove it if it has a lower weight than
            // the one we are attempting to add.
            if (removeIf(t -> t.equals(e) && t.weight() < e.weight())) {
                // If we successfully removed an entry for the same URL but with a lower weight
                // we can add the new target with the higher weight.
                return super.offer(e);
            } else {
                // If the removal failed (i.e. the target present had greater or equal weight)
                // skip adding the new target
                return false;
            }
        }
        // If the new target URL is unique, just offer as normal
        return super.offer(e);
    }

    public boolean add(String url, double weight) {
        return add(new CrawlTarget(url, weight));
    }

    public boolean addAll(String[] urls, double weight) {
        boolean modified = false;
        for (int i = 0; i < urls.length; i++) {
            modified |= add(urls[i], weight);
        }
        return modified;
    }

    public boolean addAll(Elements elements, double weight) {
        boolean modified = false;
        for (int i = 0; i < elements.size(); i++) {
            String absUrl = elements.get(i).absUrl("href");
            if (absUrl != null){
                modified |= add(absUrl, weight);
            }
        }
        return modified;
    }

    public boolean contains(String s) {
        // Since CrawlTarget.equals only compares url...
        return super.contains(new CrawlTarget(s, 0));
    }
}

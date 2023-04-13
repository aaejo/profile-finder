package io.github.aaejo.profilefinder.finder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
public abstract class BaseFinder {

    public DebugData debugData;
    protected SearchState state;

    protected final FinderClient client;
    protected final CrawlingProperties crawlingProperties;
    protected final MeterRegistry registry;

    public BaseFinder(FinderClient client, CrawlingProperties crawlingProperties, MeterRegistry registry) {
        this.client = client;
        this.crawlingProperties = crawlingProperties;
        this.registry = registry;
        this.state = SearchState.IDLE;
    }

    protected List<Element> drillDownToUniqueMain(Document page) {
        List<Element> drillDown = new ArrayList<>();
        drillDown.add(page.body());

        Element skipAnchor = page.selectFirst("a[id=main-content]:empty");
        Element mainContentBySkipAnchor = skipAnchor != null ? skipAnchor.parent() : null;
        if (mainContentBySkipAnchor != null) {
            log.debug("Found main content of {} by using skip anchor", page.location());
            drillDown.add(mainContentBySkipAnchor);
        }

        Element mainContentByMainTag = page.selectFirst("main");
        if (mainContentByMainTag != null) {
            log.debug("Found main content of {} by HTML main tag", page.location());
            drillDown.add(mainContentByMainTag);
        }

        Element mainContentByAriaRole = page.selectFirst("*[role=main]");
        if (mainContentByAriaRole != null) {
            log.debug("Found main content of {} by main ARIA role", page.location());
            drillDown.add(mainContentByAriaRole);
        }

        Element mainContentByIdMain = page.getElementById("main");
        if (mainContentByIdMain != null && mainContentByIdMain.tag().isBlock()) {
            log.debug("Found main content of {} by id = main", page.location());
            drillDown.add(mainContentByIdMain);
        }

        Element mainContentByIdContent = page.getElementById("content");
        if (mainContentByIdContent != null && mainContentByIdContent.tag().isBlock()) {
            log.debug("Found main content of {} by id = content", page.location());
            drillDown.add(mainContentByIdContent);
        }

        Element mainContentByIdMainContent = page.getElementById("main-content");
        if (mainContentByIdMainContent != null && mainContentByIdMainContent.tag().isBlock()) {
            log.debug("Found main content of {} by id = main-content", page.location());
            drillDown.add(mainContentByIdMainContent);
        }

        return drillDown.stream()
                .distinct()
                .sorted(Comparator.<Element>comparingInt(e -> e.parents().size()).reversed())
                .toList();
    }

    protected List<Element> drillDownToContent(Document page) {
        List<Element> drillDown = new ArrayList<>(drillDownToUniqueMain(page));

        // The three ways we've checked for main content so far are supposed to be unique, take the deepest one
        Element deepestUniqueMain = drillDown.get(0);

        drillDown.addAll(deepestUniqueMain
                .select("*[id^=main], *[id*=content], *[class^=main], *[class*=content]")
                .stream()
                .filter(e -> e.tag().isBlock())
                .distinct()
                .toList());

        // Sort from most to least parents
        drillDown.sort(Comparator.<Element>comparingInt(e -> e.parents().size()).reversed());

        return drillDown;
    }

    protected int tryAddLinks(CrawlQueue queue, String host, double initialWeight, Elements links) {
        int count = 0;
        for (Element addLink : links) {
            if (StringUtils.startsWith(addLink.attr("href"), "#")) { // Getting non-absolute URL for once
                log.debug("Skipping relative fragment link to item on same page");
                continue;
            }

            String href = addLink.absUrl("href");
            if (StringUtils.containsAnyIgnoreCase(addLink.text(), "Intranet")) {
                log.debug("Skipping link based on text keyword(s). Text = {}, url = {}", addLink.text(), href);
                continue;
            }

            if (tryAddLink(queue, host, initialWeight, href)) {
                count++;
            }
        }
        return count;
    }

    protected boolean tryAddLink(CrawlQueue queue, String host, double initialWeight, String url) {
        if (StringUtils.containsAny(url, crawlingProperties.disallowedHosts())) {
            log.debug("Will not crawl link to disallowed host {}", url);
            return false;
        }

        if (!StringUtils.contains(url, host)) { // Naively determine if link leads to different host
            if (crawlingProperties.offHostCrawlingAllowed()) { // If off-host crawling is allowed
                log.debug("Decreasing weight of off-site link {}", url);
                // Adjust priority of links going to different hosts
                initialWeight *= crawlingProperties.offHostCrawlingWeight();
            } else {
                log.debug("Skipping off-site link {}", url);
                return false; // Skip if off-host crawling is not allowed
            }
        }

        if (StringUtils.endsWithAny(url, ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".jpg", ".png")) {
            log.debug("Skipping link to downloadable document {}", url);
            return false;
        }

        return queue.add(url, initialWeight);
    }
}

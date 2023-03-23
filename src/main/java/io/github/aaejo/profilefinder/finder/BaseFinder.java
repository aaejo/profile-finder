package io.github.aaejo.profilefinder.finder;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import io.github.aaejo.finder.client.FinderClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseFinder {

    protected final FinderClient client;

    public BaseFinder(FinderClient client) {
        this.client = client;
    }

    protected Element drillDownToContent(Document page) {
        // Attempt to drill down to main content using
        // main tag - main
        // a tag with id including "main", no children (then get parent of the a tag) -
        // a[id*=/main/]:empty
        // div with id including "main" - div[id*=/main/]
        // div with aria role "main" - div[role=main]
        // other?

        Element skipAnchor = page.selectFirst("a[id=main-content]:empty");
        Element mainContentBySkipAnchor = skipAnchor != null ? skipAnchor.parent() : null;
        if (mainContentBySkipAnchor != null) {
            log.debug("Found main content of {} by using skip anchor", page.location());
            return mainContentBySkipAnchor;
        }

        Element mainContentByMainTag = page.selectFirst("main");
        if (mainContentByMainTag != null) {
            log.debug("Found main content of {} by HTML main tag", page.location());
            return mainContentByMainTag;
        }

        Element mainContentByAriaRole = page.selectFirst("*[role=main]");
        if (mainContentByAriaRole != null) {
            log.debug("Found main content of {} by main ARIA role", page.location());
            return mainContentByAriaRole;
        }

        // TODO: Likely want to handle non-div tags and also when there are multiple main divs
        Element mainContentByDivId = page.selectFirst("div[id^=main]");
        if (mainContentByDivId != null) {
            return mainContentByDivId;
        }

        Element contentByDivId = page.selectFirst("div[id*=content]");
        if (contentByDivId != null) {
            return contentByDivId;
        }

        return page.body();
    }
}

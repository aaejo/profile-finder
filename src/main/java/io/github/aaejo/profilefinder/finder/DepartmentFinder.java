package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.exception.DepartmentSiteNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DepartmentFinder extends BaseFinder {

    private static final List<String> COMMON_TEMPLATES = List.of(
            "https://%s/philosophy",
            "https://philosophy.%s",
            "https://%s/department/philosophy",
            "https://%s/dept/philosophy",
            "https://%s/artsci/philosophy",
            "https://%s/humanities/philosophy",
            "https://phil.%s"
        );
    private final List<DepartmentKeyword> departmentKeywords;

    public DepartmentFinder(FinderClient client) {
        super(client);

        departmentKeywords = List.of(
            new DepartmentKeyword(1.0, "philosophy"),
            new DepartmentKeyword(0.8, "humanities"),
            new DepartmentKeyword(0.8, "social science", "social-science", "socialscience"));
    }

    public double foundDepartmentSite(final Document page) {
        double confidence = 0;
        if (page == null) {
            log.info("Negative confidence that department site found at null page");
            return -10;
        }

        try {
            int statusCode = page.connection().response().statusCode();
            if (statusCode >= 400) {
                log.info("Negative confidence that department site found given HTTP {} status", statusCode);
                return -10;
            }
        } catch (IllegalArgumentException e) {
            // Protective case when trying to get response details before request has actually been made.
            // This is relevant when running from tests or using the Selenium FinderClient strategy.
            log.debug("Cannot get connection response when Jsoup was not used to make the request.");
        }

        String location = page.location();
        String title = page.title();

        for (DepartmentKeyword keyword : departmentKeywords) {
            if (StringUtils.containsAnyIgnoreCase(title, keyword.variants)) {
                if (StringUtils.containsAnyIgnoreCase(title, "Department", "School")) {
                    log.debug("Page title contains \"{}\" and either \"Department\" or \"School\". Very high confidence", keyword.variants[0]);
                    log.info("Full confidence of finding department site at {}", location);
                    confidence += 1 * keyword.weight;
                } else if (StringUtils.containsIgnoreCase(title, "Degree")) {
                    log.debug(
                            "Page title contains \"{}\" and \"Degree\". Confidence reduced, likely found degree info page instead",
                            keyword.variants[0]);
                    confidence -= 1 * keyword.weight;
                } else {
                    log.debug("Page title contains \"{}\" but no other keywords. Medium confidence added", keyword.variants[0]);
                    confidence += 0.5 * keyword.weight;
                }
            }
            
            // Images (w/ alt text) relating to philosophy that links back to same page (ie likely logos)
            Elements relevantImageLinks = page.select(keyword.relevantImageLink);
            for (Element imgLink : relevantImageLinks) {
                if (imgLink.parent().absUrl("href").equals(location)) {
                    log.debug("Found relevant image linking back to same page. High confidence added");
                    confidence += 0.8 * keyword.weight;
                }
                /*
                * NOTES:
                * - Case for a relevant image not linking back, which removes confidence?
                * - What about relevant non-link images?
                * - Should confidences scale based on number of results?
                */
                
            }
            
            // Relevantly titled text link that leads back to same page
            Elements relevantLinks = page.select(keyword.relevantLink);
            for (Element link : relevantLinks) {
                if (link.absUrl("href").equals(location)) {
                    log.debug("Found relevant link back to same page. High confidence added");
                    confidence += 0.8 * keyword.weight;
                }
                /*
                 * NOTES:
                 * - This one might need to be made to have less significant impact
                 * - Should probably refine for better text comparison. Like combining
                 *    "department" with philosophy
                 * - For this one it's probably more dangerous to scale for number of results
                 *    and/or remove confidence for non-returning links. But it can be considered
                 */
            }
            
            // Relevant heading element, scaled by heading size
            Elements relevantHeadings = page.select(keyword.relevantHeading);
            for (Element heading : relevantHeadings) {
                double modifier = keyword.weight * switch (heading.tagName()) {
                    case "h1" -> 0.85;
                    case "h2" -> 0.82;
                    case "h3" -> 0.8;
                    case "h4" -> 0.5;
                    case "h5" -> 0.3;
                    case "h6" -> 0.1;
                    default -> 0;
                };
                log.debug("{} confidence added based on relevant {}-level heading", modifier, heading.tagName());
                confidence += modifier;
                
                // NOTE: might want to refine text matching on this one too
            }
    }

        // NOTE: Might need to handle when philosophy is paired with something else
        // (religion, linguistics, etc.)

        log.info("{} confidence of finding department site at {} ", confidence, location);
        return confidence;
    }

    public Document findDepartmentSite(Institution institution, Document inPage) {
        double confidence = foundDepartmentSite(inPage);
        return findDepartmentSite(institution, inPage, confidence);
    }

    public Document findDepartmentSite(Institution institution, Document inPage, double initialConfidence) {
        CrawlQueue checkedLinks = new CrawlQueue();
        checkedLinks.add(inPage.location(), initialConfidence);

        // 1. Try some basics
        String hostname = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");

        for (String template : COMMON_TEMPLATES) {
            String templatedUrl = String.format(template, hostname);
            Document page = client.get(templatedUrl);

            double confidence = foundDepartmentSite(page);

            checkedLinks.add(templatedUrl, confidence);

            if (confidence >= 1) {
                return page;
            }
        }

        HashSet<String> flatSiteMap = client.getSiteMapURLs(inPage.location());
        CrawlQueue crawlQueue = new CrawlQueue();

        for (String url : flatSiteMap) {
            for (DepartmentKeyword keyword : departmentKeywords) {
                if (StringUtils.containsAnyIgnoreCase(url, keyword.variants)) {
                    crawlQueue.add(new CrawlTarget(url, keyword.weight));
                    break; // Break after adding so we don't bother trying to add the same url multiple times
                           // e.g. when we have multiple matches on the same url
                }
            }
        }

        CrawlTarget target;
        while ((target = crawlQueue.poll()) != null) {
            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);

            checkedLinks.add(page.location(), confidence);
            if (confidence >= 1) {
                return page;
            }
        }

        // 2. Actual crawling I guess?
        // TODO
        // (*1) NOTE: currently starting from inPage again but maybe should go from highest confidence page found from step 1?

        // 2.1 Specialized crawling

        for (DepartmentKeyword keyword : departmentKeywords) {
            Elements possibleLinks = inPage.select(keyword.relevantLink); // (*1)
            crawlQueue.addAll(possibleLinks, keyword.weight);
        }

        // TODO: Need to make sure crawling doesn't leave the site as much as possible.

        target = null;
        while ((target = crawlQueue.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);
            checkedLinks.add(target.url(), confidence);
            for (DepartmentKeyword keyword : departmentKeywords) {
                Elements possibleLinks = page.select(keyword.relevantLink);
                crawlQueue.addAll(possibleLinks, keyword.weight);
            }
        }

        // 2.2 Just crawl every link possible maybe? (maintaining checkedLinks)

        CrawlTarget best = checkedLinks.peek();

        if (best.weight() < 1) {
            throw new DepartmentSiteNotFoundException(institution, best.url(), best.weight());
        }

        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    private class DepartmentKeyword {
        String[] variants;
        double weight;
        Evaluator relevantImageLink;
        Evaluator relevantLink;
        Evaluator relevantHeading;

        public DepartmentKeyword(double weight, String... variants) {
            this.variants = variants;
            this.weight = weight;

            String relevantImageLinkQuery = "a[href] img[alt*=" + variants[0] + "]";
            String relevantLinkQuery = "a[href]:contains(" + variants[0] + ")";
            String relevantHeadingQuery = "h1:contains(" + variants[0] + "), "
                                        + "h2:contains(" + variants[0] + "), "
                                        + "h3:contains(" + variants[0] + "), "
                                        + "h4:contains(" + variants[0] + "), "
                                        + "h5:contains(" + variants[0] + "), "
                                        + "h6:contains(" + variants[0] + ") ";
            if (variants.length > 1) {
                for (int i = 1; i < variants.length; i++) {
                    relevantImageLinkQuery += ", a[href] img[alt*=" + variants[i] + "]";
                    relevantLinkQuery += ", a[href]:contains(" + variants[i] + ")";
                    relevantHeadingQuery += ", h1:contains(" + variants[i] + "), "
                                            + "h2:contains(" + variants[i] + "), "
                                            + "h3:contains(" + variants[i] + "), "
                                            + "h4:contains(" + variants[i] + "), "
                                            + "h5:contains(" + variants[i] + "), "
                                            + "h6:contains(" + variants[i] + ") ";
                }
            }
            this.relevantImageLink = QueryParser.parse(relevantImageLinkQuery);
            this.relevantLink = QueryParser.parse(relevantLinkQuery);
            this.relevantHeading = QueryParser.parse(relevantHeadingQuery);
        }
    }
}

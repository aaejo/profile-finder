package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.factory.primitive.ObjectDoubleMaps;
import org.eclipse.collections.api.map.primitive.ImmutableObjectDoubleMap;
import org.eclipse.collections.api.tuple.primitive.ObjectDoublePair;
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

    public static final String DEPARTMENT_NAME = "Philosophy";
    private static final List<String> COMMON_TEMPLATES = List.of(
            "https://%s/philosophy",
            "https://philosophy.%s",
            "https://%s/department/philosophy",
            "https://%s/dept/philosophy",
            "https://%s/artsci/philosophy",
            "https://%s/humanities/philosophy",
            "https://phil.%s"
        );
    private static ImmutableObjectDoubleMap<String> VALID_DEPARTMENT_WEIGHTS = ObjectDoubleMaps.mutable.<String>empty()
        .withKeyValue("philosophy",     1.0)
        .withKeyValue("humanities",     0.8)
        .withKeyValue("social science", 0.8)
        .withKeyValue("social-science", 0.8)
        .withKeyValue("socialscience",  0.8)
        .toImmutable();

    public DepartmentFinder(FinderClient client) {
        super(client);
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

        if (StringUtils.containsIgnoreCase(title, DEPARTMENT_NAME)) {
            if (StringUtils.containsAnyIgnoreCase(title, "Department", "School")) {
                log.debug("Page title contains \"{}\" and either \"Department\" or \"School\". Very high confidence", DEPARTMENT_NAME);
                log.info("Full confidence of finding department site at {}", location);
                return 1;
            } else if (StringUtils.containsIgnoreCase(title, "Degree")) {
                log.debug(
                        "Page title contains \"{}\" and \"Degree\". Confidence reduced, likely found degree info page instead",
                        DEPARTMENT_NAME);
                confidence -= 1;
            } else {
                log.debug("Page title contains \"{}\" but no other keywords. Medium confidence added", DEPARTMENT_NAME);
                confidence += 0.5;
            }
        }

        // Images (w/ alt text) relating to philosophy that links back to same page (ie
        // likely logos)
        Elements relevantImageLinks = page.select(Evaluators.RELEVANT_IMAGE_LINK);
        for (Element imgLink : relevantImageLinks) {
            if (imgLink.parent().absUrl("href").equals(location)) {
                log.debug("Found relevant image linking back to same page. High confidence added");
                confidence += 0.8;
            }
            /*
             * NOTES:
             * - Case for a relevant image not linking back, which removes confidence?
             * - What about relevant non-link images?
             * - Should confidences scale based on number of results?
             */

        }

        // Relevantly titled text link that leads back to same page
        Elements relevantLinks = page.select(Evaluators.RELEVANT_LINK);
        for (Element link : relevantLinks) {
            if (link.absUrl("href").equals(location)) {
                log.debug("Found relevant link back to same page. High confidence added");
                confidence += 0.8;
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
        Elements relevantHeadings = page.select(Evaluators.RELEVANT_HEADING);
        for (Element heading : relevantHeadings) {
            double modifier = switch (heading.tagName()) {
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
        String hostname = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");
        CrawlQueue checkedLinks = new CrawlQueue();
        checkedLinks.add(inPage.location(), initialConfidence);

        // 1. Try some basics
        Document candidate = inPage;
        double candidateConfidence = initialConfidence;

        for (String template : COMMON_TEMPLATES) {
            String templatedUrl = String.format(template, hostname);
            Document page = client.get(templatedUrl);

            double confidence = foundDepartmentSite(page);

            checkedLinks.add(templatedUrl, confidence);

            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }
        }

        HashSet<String> flatSiteMap = client.getSiteMapURLs(inPage.location());
        CrawlQueue crawlQueue = new CrawlQueue();

        for (String url : flatSiteMap) {
            for (ObjectDoublePair<String> deptPat : VALID_DEPARTMENT_WEIGHTS.keyValuesView()) {
                if (StringUtils.containsIgnoreCase(url, deptPat.getOne())) {
                    crawlQueue.add(new CrawlTarget(url, deptPat.getTwo()));
                    break; // Break after adding so we don't bother trying to add the same url multiple times
                           // e.g. when we have multiple matches on the same url
                }
            }
        }

        for (CrawlTarget target : crawlQueue) {
            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);

            checkedLinks.add(page.location(), confidence);
            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }
        }

        // 2. Actual crawling I guess?
        // TODO
        // (*1) NOTE: currently starting from inPage again but maybe should go from highest confidence page found from step 1?

        // Explicitly reset candidate and confidence (*1)
        candidate = inPage;
        candidateConfidence = initialConfidence;

        // 2.1 Specialized crawling

        // TODO: Expand list, but also try them one at a time and maybe not as a single query
        //  this would let us handle them by decreasing priority (might be affected by *1)
        //  but how would that work at deeper levels?
        String possibleLinkSelector = "a[href]:contains(" + DEPARTMENT_NAME + "), "
                                    + "a[href]:contains(humanities), "
                                    + "a[href]:contains(arts and sciences)";

        Elements possibleLinks = inPage.select(possibleLinkSelector); // (*1)
        CrawlQueue crawlQueue2 = new CrawlQueue(possibleLinks.size());
        crawlQueue2.addAll(possibleLinks, 1.0);

        // TODO: Need to make sure crawling doesn't leave the site as much as possible.

        CrawlTarget target;
        while ((target = crawlQueue2.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);
            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }

            checkedLinks.add(target.url(), confidence);
            Elements additionalLinks = page.select(possibleLinkSelector);
            crawlQueue2.addAll(additionalLinks, 1.0);
        }

        // 2.2 Just crawl every link possible maybe? (maintaining checkedLinks)
        // Also, sitemaps exist

        if (candidateConfidence < 1) {
            throw new DepartmentSiteNotFoundException(institution, candidate.location(), candidateConfidence);
        }

        return candidate;
    }

    private static class Evaluators {

        static final Evaluator RELEVANT_IMAGE_LINK = QueryParser.parse("a[href] img[alt*=" + DEPARTMENT_NAME + "]");
        static final Evaluator RELEVANT_LINK = QueryParser.parse("a[href]:contains(" + DEPARTMENT_NAME + ")");
        static final Evaluator RELEVANT_HEADING =
                QueryParser.parse("h1:contains(" + DEPARTMENT_NAME + "), "
                                + "h2:contains(" + DEPARTMENT_NAME + "), "
                                + "h3:contains(" + DEPARTMENT_NAME + "), "
                                + "h4:contains(" + DEPARTMENT_NAME + "), "
                                + "h5:contains(" + DEPARTMENT_NAME + "), "
                                + "h6:contains(" + DEPARTMENT_NAME + ") ");
    }
}

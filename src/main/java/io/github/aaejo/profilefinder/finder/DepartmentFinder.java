package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.map.primitive.ImmutableObjectDoubleMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.github.aaejo.profilefinder.finder.configuration.DepartmentFinderProperties;
import io.github.aaejo.profilefinder.finder.exception.DepartmentSiteNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
@Service
public class DepartmentFinder extends BaseFinder {

    @Autowired
    private KafkaTemplate<String, DebugData> debugTemplate;

    private final DepartmentFinderProperties properties;

    public DepartmentFinder(FinderClient client, DepartmentFinderProperties properties, CrawlingProperties crawlingProperties) {
        super(client, crawlingProperties);
        this.properties = properties;
    }

    public double foundDepartmentSite(final Document page) {
        ImmutableObjectDoubleMap<DepartmentKeyword> report = foundDepartmentSiteDetailed(page);
        if (report.size() == 1 && report.containsKey(DepartmentKeyword.UNDEFINED)) {
            return report.get(DepartmentKeyword.UNDEFINED);
        }
        double confidence = report
                .keyValuesView()
                .collectDouble(kw -> kw.getTwo() * kw.getOne().getWeight()) // Scale each confidence by keyword weight
                .sum(); // Sum confidences
        log.info("{} confidence of finding department site at {} ", confidence, page.location());
        return confidence;
    }

    public ImmutableObjectDoubleMap<DepartmentKeyword> foundDepartmentSiteDetailed(final Document page) {
        if (page == null) {
            log.info("Negative confidence that department site found at null page");
            return ObjectDoubleHashMap.<DepartmentKeyword>newWithKeysValues(DepartmentKeyword.UNDEFINED, -10)
                    .toImmutable();
        }

        try {
            int statusCode = page.connection().response().statusCode();
            if (statusCode >= 400) {
                log.info("Negative confidence that department site found given HTTP {} status", statusCode);
                return ObjectDoubleHashMap.<DepartmentKeyword>newWithKeysValues(DepartmentKeyword.UNDEFINED, -10)
                        .toImmutable();
            }
        } catch (IllegalArgumentException e) {
            // Protective case when trying to get response details before request has actually been made.
            // This is relevant when running from tests or using the Selenium FinderClient strategy.
            log.debug("Cannot get connection response when Jsoup was not used to make the request.");
        }

        ObjectDoubleHashMap<DepartmentKeyword> confidence = ObjectDoubleHashMap.newMap();
        String location = page.location();
        String title = page.title();

        for (DepartmentKeyword keyword : properties.getImportantDepartmentKeywords()) {
            confidence.addToValue(keyword, 0);

            if (StringUtils.containsAnyIgnoreCase(title, keyword.getVariants())
                    || StringUtils.containsAnyIgnoreCase(location, keyword.getVariants())) {
                HashSet<String> tokenizedIdentifiers = new HashSet<>();
                Arrays.stream(location.split("\\W"))
                        .map(token -> token.toLowerCase())
                        .forEach(token -> tokenizedIdentifiers.add(token));
                Arrays.stream(title.split(" "))
                        .map(token -> token.toLowerCase())
                        .forEach(token -> tokenizedIdentifiers.add(token));

                for (String token : tokenizedIdentifiers) {
                    double modifier;
                    if (StringUtils.equalsAnyIgnoreCase(token, keyword.getVariants())) {
                        modifier = 0.5;
                    } else {
                        modifier = switch (token) {
                            case "department", "school" -> 0.5;
                            case "degree"               -> -1.0;
                            case "calendar"             -> -5.0;
                            default -> 0.0;
                        };
                    }

                    log.debug("{} confidence change based on token {} present in page title or location", modifier, token);
                    confidence.addToValue(keyword, modifier);
                }
            }

            // Images (w/ alt text) relating to philosophy that links back to same page (ie likely logos)
            Elements relevantImageLinks = page.select(keyword.getRelevantImageLink());
            for (Element imgLink : relevantImageLinks) {
                if (imgLink.parent().absUrl("href").equals(location)) {
                    log.debug("Found relevant image linking back to same page. High confidence added");
                    confidence.addToValue(keyword, 0.8);
                }
                /*
                * NOTES:
                * - Case for a relevant image not linking back, which removes confidence?
                * - What about relevant non-link images?
                * - Should confidences scale based on number of results?
                */

            }

            // Relevantly titled text link that leads back to same page
            Elements relevantLinks = page.select(keyword.getRelevantLink());
            for (Element link : relevantLinks) {
                if (link.absUrl("href").equals(location)) {
                    log.debug("Found relevant link back to same page. High confidence added");
                    confidence.addToValue(keyword, 0.8);
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
            Elements relevantHeadings = page.select(keyword.getRelevantHeading());
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
                confidence.addToValue(keyword, modifier);
            }
        }

        return confidence.toImmutable();
    }

    public Document findDepartmentSite(Institution institution, Document inPage) {
        double confidence = foundDepartmentSite(inPage);
        return findDepartmentSite(institution, inPage, confidence);
    }

    public Document findDepartmentSite(Institution institution, Document inPage, double initialConfidence) {
        CrawlQueue checkedLinks = new CrawlQueue();
        checkedLinks.add(inPage.location(), initialConfidence);
        debugData = new DebugData();
        debugData.institution = institution;
        debugData.checkedLinks = checkedLinks;

        // 1. Try some basics
        String hostname = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");

        for (String template : properties.getTemplates()) {
            String templatedUrl = String.format(template, hostname);
            Document page = client.get(templatedUrl);

            double confidence = foundDepartmentSite(page);

            if (page != null) {
                // ...
                checkedLinks.add(page.location(), confidence);
            } else {
                checkedLinks.add(templatedUrl, confidence);
            }

            if (confidence >= 1.4) {
                debugData.details = "Templating";
                debugTemplate.send("department.debug", institution.name(), debugData);
                log.info("Identified {} as department page with {} confidence", page.location(), confidence);
                return page;
            }
        }

        HashSet<String> flatSiteMap = client.getSiteMapURLs(inPage.location());
        String host = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");
        CrawlQueue crawlQueue = new CrawlQueue();

        for (String url : flatSiteMap) {
            for (DepartmentKeyword keyword : properties.getKeywords()) {
                if (StringUtils.containsAnyIgnoreCase(url, keyword.getVariants())) {
                    tryAddLink(crawlQueue, host, keyword.getWeight(), url);
                }
            }
        }

        CrawlTarget target;
        while ((target = crawlQueue.poll()) != null) {
            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);

            if (page != null && !target.url().equals(page.location())) {
                // ...
                checkedLinks.add(page.location(), confidence);
            }
            checkedLinks.add(target.url(), confidence);
            if (confidence >= 1.4) {
                debugData.details = "SiteMap";
                debugTemplate.send("department.debug", institution.name(), debugData);
                log.info("Identified {} as department page with {} confidence", target.url(), target.weight());
                return page;
            }
        }

        // 2. Actual crawling I guess?
        // (*1) NOTE: currently starting from inPage again but maybe should go from highest confidence page found from step 1?

        // 2.1 Specialized crawling

        queueLinksFromPage(crawlQueue, inPage, initialConfidence, institution); // (*1)

        target = null;
        while ((target = crawlQueue.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            Document page = client.get(target.url());

            double confidence = foundDepartmentSite(page);
            if (page != null && !target.url().equals(page.location())) {
                // ...
                checkedLinks.add(page.location(), confidence);
            }
            checkedLinks.add(target.url(), confidence);
            queueLinksFromPage(crawlQueue, page, confidence, institution);
        }

        // 2.2 Just crawl every link possible maybe? (maintaining checkedLinks)

        CrawlTarget best = checkedLinks.peek();

        debugData.details = "Crawling";
        debugTemplate.send("department.debug", institution.name(), debugData);
        if (best.weight() < 1) {
            throw new DepartmentSiteNotFoundException(institution, best.url(), best.weight());
        }

        log.info("Identified {} as department page with {} confidence", best.url(), best.weight());
        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    public DepartmentKeyword getPrimaryDepartment() {
        return properties.getPrimary();
    }

    public String[] getImportantDepartmentVariants() {
        return properties.getImportantDepartmentVariants();
    }

    private int queueLinksFromPage(CrawlQueue queue, Document page, double pageConfidence, Institution institution) {
        if (page == null) {
            return -1;
        }

        int count = 0;
        String host = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");
        for (DepartmentKeyword keyword : properties.getKeywords()) {
            // Scale target's weight in queue by keyword weight and page confidence. Crawl targets will be left with
            // their highest found weight in the queue because of the logic in CrawlQueue.offer
            double weight = pageConfidence * keyword.getWeight();
            Elements possibleLinks = page.select(keyword.getRelevantLink());
            count += tryAddLinks(queue, host, weight, possibleLinks);
        }

        return count;
    }
}

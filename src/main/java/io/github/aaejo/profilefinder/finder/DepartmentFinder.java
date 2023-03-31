package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.map.primitive.ImmutableObjectDoubleMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

    private final List<String> commonTemplates;
    private final List<DepartmentKeyword> departmentKeywords;

    public DepartmentFinder(FinderClient client, DepartmentFinderProperties properties, CrawlingProperties crawlingProperties) {
        super(client, crawlingProperties);
        this.commonTemplates = properties.templates();
        this.departmentKeywords = properties.keywords();
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

        for (DepartmentKeyword keyword : departmentKeywords) {
            if (keyword.getWeight() <= 0.01) {
                continue; // <= 0.01 weight keywords are only for crawl direction
            }

            confidence.addToValue(keyword, 0);

            if (StringUtils.containsAnyIgnoreCase(title, keyword.getVariants())) {
                if (StringUtils.containsAnyIgnoreCase(title, "Department", "School")) {
                    log.debug("Page title contains \"{}\" and either \"Department\" or \"School\". Very high confidence", keyword.getVariants()[0]);
                    confidence.addToValue(keyword, 1);
                } else if (StringUtils.containsIgnoreCase(title, "Degree")) {
                    log.debug(
                            "Page title contains \"{}\" and \"Degree\". Confidence reduced, likely found degree info page instead",
                            keyword.getVariants()[0]);
                            confidence.addToValue(keyword, -1);
                } else {
                    log.debug("Page title contains \"{}\" but no other keywords. Medium confidence added", keyword.getVariants()[0]);
                    confidence.addToValue(keyword, 0.5);
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

        // 1. Try some basics
        String hostname = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");

        for (String template : commonTemplates) {
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
                if (StringUtils.containsAnyIgnoreCase(url, keyword.getVariants())) {
                    crawlQueue.add(new CrawlTarget(url, keyword.getWeight()));
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
            checkedLinks.add(target.url(), confidence);
            queueLinksFromPage(crawlQueue, page, confidence, institution);
        }

        // 2.2 Just crawl every link possible maybe? (maintaining checkedLinks)

        CrawlTarget best = checkedLinks.peek();

        if (best.weight() < 1) {
            throw new DepartmentSiteNotFoundException(institution, best.url(), best.weight());
        }

        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    public DepartmentKeyword getPrimaryDepartment() {
        return departmentKeywords.stream().filter(DepartmentKeyword::isPrimary).findFirst().get();
    }

    public String[] getRelevantDepartmentVariants() {
        List<String> variants = new ArrayList<>();
        for (DepartmentKeyword kw : departmentKeywords) {
            if (kw.getWeight() > 0.01) {
                variants.addAll(Arrays.asList(kw.getVariants()));
            }
        }
        String[] varArray = new String[variants.size()];
        variants.toArray(varArray);
        return varArray;
    }

    private int queueLinksFromPage(CrawlQueue queue, Document page, double pageConfidence, Institution institution) {
        if (page == null) {
            return -1;
        }

        int count = 0;
        String host = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");
        for (DepartmentKeyword keyword : departmentKeywords) {
            // Scale target's weight in queue by keyword weight and page confidence. Crawl targets will be left with
            // their highest found weight in the queue because of the logic in CrawlQueue.offer
            double weight = pageConfidence * keyword.getWeight();
            Elements possibleLinks = page.select(keyword.getRelevantLink());
            count += tryAddLinks(queue, host, weight, possibleLinks);
        }

        return count;
    }
}

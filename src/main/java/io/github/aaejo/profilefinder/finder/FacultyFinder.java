package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.finder.client.FinderClientResponse;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.github.aaejo.profilefinder.finder.configuration.DepartmentFinderProperties;
import io.github.aaejo.profilefinder.finder.exception.FacultyListNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * @author Omri Harary
 */
@Service
public class FacultyFinder extends BaseFinder {
    private static final Logger log = LoggerFactory.getLogger(FacultyFinder.class);

    @Autowired
    private KafkaTemplate<String, DebugData> debugTemplate;

    private final DepartmentFinderProperties departmentFinderProperties;

    public FacultyFinder(FinderClient client, DepartmentFinderProperties departmentFinderProperties,
            CrawlingProperties crawlingProperties, MeterRegistry registry) {
        super(client, crawlingProperties, registry);

        this.departmentFinderProperties = departmentFinderProperties;
    }

    public double foundFacultyList(final FinderClientResponse page) {
        double confidence = 0;
        if (page == null || page.document() == null) {
            log.info("Negative confidence that faculty list found at null page");
            return -10;
        }

        if (!page.isSuccess()) {
            log.info("Negative confidence that faculty list found given HTTP {} status", page.status());
            return -10;
        }

        String location = page.location();
        String title = page.document().title();

        HashSet<String> tokenizedIdentifiers = new HashSet<>();
        Arrays.stream(location.split("\\W"))
                .map(token -> token.toLowerCase())
                .forEach(token -> tokenizedIdentifiers.add(token));
        Arrays.stream(title.split(" "))
                .map(token -> token.toLowerCase())
                .forEach(token -> tokenizedIdentifiers.add(token));
        for (String token : tokenizedIdentifiers) {
            // NOTE: May need to adjust weights here.
            double modifier = switch (token) {
                case "professors"           -> 0.7;
                case "instructors"          -> 0.5;
                case "faculty"              -> 0.4;
                case "staff"                -> 0.4;
                case "people", "persons"    -> 0.37;
                case "profiles"             -> 0.35;
                case "members"              -> 0.35;
                case "employee"             -> 0.25;
                case "directory"            -> 0.1;
                case "contact"              -> 0.1;
                default -> 0.0;
            };

            confidence += modifier;
        }

        Element content = drillDownToUniqueMain(page.document()).get(0);

        // TODO: These should be capped to a max number so that one page doesn't end up way higher than another just by
        //      having more matches
        confidence += 0.03 * StringUtils.countMatches(content.text().toLowerCase(), "professor");
        confidence += 0.02 * StringUtils.countMatches(content.text().toLowerCase(), "lecturer");
        confidence += 0.01 * StringUtils.countMatches(content.text().toLowerCase(), "prof.");
        confidence += 0.01 * StringUtils.countMatches(content.text().toLowerCase(), "prof ");
        confidence += 0.01 * StringUtils.countMatches(content.text().toLowerCase(), "dr.");
        confidence += 0.01 * StringUtils.countMatches(content.text().toLowerCase(), "dr ");

        Elements mailLinks = content.select("a[href^=mailto:]");
        confidence += 0.05 * mailLinks.size();
        Elements telLinks = content.select("a[href^=tel:]");
        confidence += 0.03 * telLinks.size();

        Elements images = content.getElementsByTag("img");
        confidence += 0.001 * images.size();

        Elements relevantHeadings = content.select(Evaluators.RELEVANT_HEADINGS);
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
        }

        Elements tables = content.getElementsByTag("table");
        if (!tables.isEmpty()) {
            confidence += (5 - tables.size()) / 5.0; // 1-4 tables = good, more is increasingly worse 
        }

        boolean anyMatchedKeywords = false;
        for (DepartmentKeyword keyword : departmentFinderProperties.getImportantDepartmentKeywords()) {
            if (StringUtils.containsAnyIgnoreCase(location, keyword.getVariants())) {
                anyMatchedKeywords = true;
                confidence *= keyword.getWeight();
                break; // Only do this for the first matched keyword
            }
        }
        if (!anyMatchedKeywords) {
            confidence *= 0.4;
        }

        log.info("{} confidence of finding faculty list at {} ", confidence, location);
        return confidence;
    }

    public FinderClientResponse findFacultyList(Institution institution, FinderClientResponse inPage, double initialConfidence) {
        CrawlQueue checkedLinks = new CrawlQueue();
        checkedLinks.add(inPage.location(), initialConfidence);
        debugData = new DebugData();
        debugData.institution = institution;
        debugData.checkedLinks = checkedLinks;

        CrawlQueue crawlQueue = new CrawlQueue();
        queueLinksFromPage(crawlQueue, inPage, initialConfidence, institution);
        CrawlQueue secondaryCrawlQueue = new CrawlQueue();

        // 1. Crawl just the links from the department page

        CrawlTarget target;
        while ((target = crawlQueue.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            FinderClientResponse page = client.get(target.url());

            double confidence = foundFacultyList(page);
            if (page != null && !target.url().equals(page.location())) {
                // ...
                checkedLinks.add(page.location(), confidence);
            }
            checkedLinks.add(target.url(), confidence);
            queueLinksFromPage(secondaryCrawlQueue, page, confidence, institution);
        }

        // Early-exit if found a really good result just from the first batch

        CrawlTarget earlyBest = checkedLinks.peek();
        if (earlyBest.weight() >= 1.4) {
            debugData.details = "Early";
            debugTemplate.send("faculty.debug", institution.name(), debugData);
            registry.counter("jds.profile-finder.faculty-finder.found",
                    "country", institution.country(),
                    "mechanism", "crawling")
                    .increment();
            log.info("Identified {} as faculty list page with {} confidence", earlyBest.url(), earlyBest.weight());
            return client.get(earlyBest.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
        }

        // 2. Full crawling from there

        crawlQueue = secondaryCrawlQueue;
        target = null;
        while ((target = crawlQueue.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            FinderClientResponse page = client.get(target.url());

            double confidence = foundFacultyList(page);
            if (page != null && !target.url().equals(page.location())) {
                // ...
                checkedLinks.add(page.location(), confidence);
            }
            checkedLinks.add(target.url(), confidence);
            queueLinksFromPage(crawlQueue, page, confidence, institution);
        }

        CrawlTarget best = checkedLinks.peek();

        debugTemplate.send("faculty.debug", institution.name(), debugData);
        if (best.weight() < 1) {
            throw new FacultyListNotFoundException(institution, best.url(), best.weight());
        }

        registry.counter("jds.profile-finder.faculty-finder.found",
                "country", institution.country(),
                "mechanism", "crawling")
                .increment();
        log.info("Identified {} as faculty list page with {} confidence", best.url(), best.weight());
        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    private int queueLinksFromPage(CrawlQueue queue, FinderClientResponse page, double pageConfidence, Institution institution) {
        if (page == null || page.document() == null) {
            return -1;
        }

        int count = 0;

        String host = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");
        List<Element> contentDrillDown = drillDownToContent(page.document());
        for (Element level : contentDrillDown) { // Reminder: Order is deepest to shallowest "main"/"content" element
            // Scale down weight the less drilled down we are. Crawl targets will be left with
            // their highest found weight in the queue because of the logic in CrawlQueue.offer
            double weight = pageConfidence
                    * ((level.parents().size() + 1) / (contentDrillDown.get(0).parents().size() + 1));
            Elements possibleLinks = level.select(Evaluators.POSSIBLE_LINK);
            count += tryAddLinks(queue, host, weight, possibleLinks);
        }

        return count;
    }

    private static class Evaluators {
        static final Evaluator POSSIBLE_LINK =
                QueryParser.parse("a[href]:contains(faculty):not(:contains(faculty of)), "
                        + "a[href]:contains(staff), a[href]:contains(people), a[href]:contains(professors), "
                        + "a[href]:contains(directory)");
        static final Evaluator RELEVANT_HEADINGS =
                QueryParser.parse(
                        "h1:contains(professors), h1:contains(instructors), h1:contains(faculty):not(:contains(faculty of)), h1:contains(staff), h1:contains(people), h1:contains(persons), h1:contains(members), h1:contains(employee), h1:contains(directory), h1:contains(contact), h1:contains(profiles), "
                        + "h2:contains(professors), h2:contains(instructors), h2:contains(faculty):not(:contains(faculty of)), h2:contains(staff), h2:contains(people), h2:contains(persons), h2:contains(members), h2:contains(employee), h2:contains(directory), h2:contains(contact), h2:contains(profiles), "
                        + "h3:contains(professors), h3:contains(instructors), h3:contains(faculty):not(:contains(faculty of)), h3:contains(staff), h3:contains(people), h3:contains(persons), h3:contains(members), h3:contains(employee), h3:contains(directory), h3:contains(contact), h3:contains(profiles), "
                        + "h4:contains(professors), h4:contains(instructors), h4:contains(faculty):not(:contains(faculty of)), h4:contains(staff), h4:contains(people), h4:contains(persons), h4:contains(members), h4:contains(employee), h4:contains(directory), h4:contains(contact), h4:contains(profiles), "
                        + "h5:contains(professors), h5:contains(instructors), h5:contains(faculty):not(:contains(faculty of)), h5:contains(staff), h5:contains(people), h5:contains(persons), h5:contains(members), h5:contains(employee), h5:contains(directory), h5:contains(contact), h5:contains(profiles), "
                        + "h6:contains(professors), h6:contains(instructors), h6:contains(faculty):not(:contains(faculty of)), h6:contains(staff), h6:contains(people), h6:contains(persons), h6:contains(members), h6:contains(employee), h6:contains(directory), h6:contains(contact), h6:contains(profiles)");
    }
}

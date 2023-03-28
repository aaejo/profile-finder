package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.Arrays;
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
import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.github.aaejo.profilefinder.finder.exception.FacultyListNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FacultyFinder extends BaseFinder {

    public FacultyFinder(FinderClient client, CrawlingProperties crawlingProperties) {
        super(client, crawlingProperties);
    }

    public double foundFacultyList(final Document page) {
        double confidence = 0;
        if (page == null) {
            log.info("0 confidence that faculty list found at null page");
            return -10;
        }

        try {
            int statusCode = page.connection().response().statusCode();
            if (statusCode >= 400) {
                log.info("0 confidence that faculty list found given HTTP {} status", statusCode);
                return -10;
            }
        } catch (IllegalArgumentException e) {
            // Protective case when trying to get response details before request has actually been made.
            // This is effectively only relevant when running from unit tests.
            log.debug("Cannot get connection response before request has been executed. This should only occur during tests.");
        }

        String location = page.location();
        String title = page.title();

        HashSet<String> tokenizedIdentifiers = new HashSet<>();
        tokenizedIdentifiers.addAll(Arrays.asList(location.split("\\W")));
        tokenizedIdentifiers.addAll(Arrays.asList(title.split(" ")));
        for (String token : tokenizedIdentifiers) {
            // NOTE: May need to adjust weights here.
            double modifier = switch (token.toLowerCase()) {
                case "professors"           -> 0.8;
                case "instructors"          -> 0.7;
                case "faculty"              -> 0.5;
                case "staff"                -> 0.5;
                case "people", "persons"    -> 0.45;
                case "employee"             -> 0.4;
                case "directory"            -> 0.4;
                case "contact"              -> 0.4;
                default -> 0.0;
            };

            confidence += modifier;
        }

        Element content = drillDownToUniqueMain(page).get(0);

        confidence += 0.03 * StringUtils.countMatches(content.text().toLowerCase(), "professor");
        confidence += 0.02 * StringUtils.countMatches(content.text().toLowerCase(), "lecturer");
        confidence += 0.01 * StringUtils.countMatches(content.text().toLowerCase(), "prof.");

        Elements mailLinks = content.select("a[href^=mailto:]");
        confidence += 0.05 * mailLinks.size();
        Elements telLinks = content.select("a[href^=tel:]");
        confidence += 0.03 * telLinks.size();

        Elements unorderedLists = content.getElementsByTag("ul");
        Elements orderedLists = content.getElementsByTag("ol");
        Elements tables = content.getElementsByTag("table");

        log.info("{} confidence of finding faculty list at {} ", confidence, location);
        return confidence;
    }

    public Document findFacultyList(Institution institution, Document inPage) {
        double confidence = foundFacultyList(inPage);
        return findFacultyList(institution, inPage, confidence);
    }

    public Document findFacultyList(Institution institution, Document inPage, double initialConfidence) {
        CrawlQueue checkedLinks = new CrawlQueue();
        CrawlQueue crawlQueue = new CrawlQueue();
        queueLinksFromPage(crawlQueue, inPage, initialConfidence);

        checkedLinks.add(inPage.location(), initialConfidence);

        CrawlTarget target;
        while ((target = crawlQueue.poll()) != null) {
            if (checkedLinks.contains(target)) {
                log.info("Skipping checked link {}", target.url()); // TODO: make debug later
                continue; // Skip if this is a URL that has already been checked
            }

            Document page = client.get(target.url());

            double confidence = foundFacultyList(page);
            checkedLinks.add(target.url(), confidence);
            queueLinksFromPage(crawlQueue, page, confidence);
        }

        CrawlTarget best = checkedLinks.peek();

        if (best.weight() < 1) {
            throw new FacultyListNotFoundException(institution, best.url(), best.weight());
        }

        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    private int queueLinksFromPage(CrawlQueue queue, Document page, double pageConfidence) {
        if (page == null) {
            return -1;
        }

        int count = 0;

        String host = StringUtils.removeStart(URI.create(page.location()).getHost(), "www.");
        List<Element> contentDrillDown = drillDownToContent(page);
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
                        +"a[href]:contains(staff), a[href]:contains(people)");
    }
}

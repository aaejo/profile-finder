package io.github.aaejo.profilefinder.finder;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.exception.FacultyListNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FacultyFinder extends BaseFinder {

    public FacultyFinder(FinderClient client) {
        super(client);
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
                case "people", "persons"    -> 0.6;
                case "faculty"              -> 0.5;
                case "staff"                -> 0.5;
                case "employee"             -> 0.4;
                case "directory"            -> 0.4;
                default -> 0.0;
            };

            confidence += modifier;
        }

        Element content = drillDownToContent(page);

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
        crawlQueue.addAll(inPage.select(Evaluators.POSSIBLE_LINK), initialConfidence);

        Document candidate = inPage;
        double candidateConfidence = initialConfidence;
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

            if (page != null) {
                String host = StringUtils.removeStart(URI.create(page.location()).getHost(), "www.");
                Elements additionalLinks = page.select(Evaluators.POSSIBLE_LINK);
                for (Element addLink : additionalLinks) {
                    double weight = confidence;
                    // Naive determination of if link leads to different host
                    if (!StringUtils.contains(addLink.absUrl("href"), host)) {
                        weight *= 0.0001; // Way reduce priority of links going to different hosts
                    }
                    crawlQueue.add(addLink.absUrl("href"), weight);
                }
            }
        }

        CrawlTarget best = checkedLinks.peek();

        if (best.weight() < 1) {
            throw new FacultyListNotFoundException(institution, candidate.location(), candidateConfidence);
        }

        return client.get(best.url()); // FIXME: This isn't great. What happens if we fail to get it this time?
    }

    private static class Evaluators {
        static final Evaluator POSSIBLE_LINK =
                QueryParser.parse("a[href]:contains(faculty):not(:contains(faculty of)), "
                        +"a[href]:contains(staff), a[href]:contains(people)");
    }
}

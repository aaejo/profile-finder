package io.github.aaejo.profilefinder.finder;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DepartmentFinder {

    public static final String DEPARTMENT_NAME = "Philosophy";
    private static final String[] COMMON_TEMPLATES = {
            "https://%s/philosophy",
            "https://philosophy.%s",
            "https://phil.%s",
            "https://%s/department/philosophy",
            "https://%s/dept/philosophy",
            "https://%s/artsci/philosophy",
            "https://%s/humanities/philosophy",
    };

    public double foundDepartmentSite(final Document page) {
        double confidence = 0;
        if (page == null) {
            log.info("Negative confidence that department site found at null page");
            return -10;
        }

        String location = page.location();

        try {
            int statusCode = page.connection().response().statusCode();
            if (statusCode >= 400) {
                log.info("Negative confidence that department site found given HTTP {} status", statusCode);
                return -10;
            }
        } catch (IllegalArgumentException e) {
            // Protective case when trying to get response details before request has actually been made.
            // This is effectively only relevant when running from unit tests.
            log.debug("Cannot get connection response before request has been executed. This should only occur during tests.");
        }

        String title = page.title();

        if (StringUtils.containsIgnoreCase(title, DEPARTMENT_NAME)) {
            if (StringUtils.containsIgnoreCase(title, "Department")) {
                log.debug("Page title contains \"{}\" and \"Department\". Very high confidence", DEPARTMENT_NAME);
                log.info("Full confidence of finding department site at {}", location);
                return 1;
            } else if (StringUtils.containsIgnoreCase(title, "Degree")) {
                log.debug(
                        "Page title contains \"{}\" and \"Degree\". Low confidence added, may have found degree info page instead",
                        DEPARTMENT_NAME);
                confidence += 0.2;
            } else {
                log.debug("Page title contains \"{}\" but no other keywords. Medium confidence added", DEPARTMENT_NAME);
                confidence += 0.5;
            }
        }

        // Images (w/ alt text) relating to philosophy that links back to same page (ie
        // likely logos)
        Elements relevantImageLinks = page.select("a[href] img[alt*=" + DEPARTMENT_NAME + "]");
        for (Element imgLink : relevantImageLinks) {
            if (imgLink.parent().attr("abs:href").equals(location)) {
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
        Elements relevantLinks = page.select("a[href]:contains(" + DEPARTMENT_NAME + ")");
        for (Element link : relevantLinks) {
            if (link.attr("abs:href").equals(location)) {
                log.debug("Found relevant link back to same page. High confidence added");
                confidence += 0.8;
            }
            /*
             * NOTES:
             * - This one might need to be made weaker
             * - Should probably refine for better text comparison. Like combining
             *    "department" with philosophy
             * - For this one it's probably more dangerous to scale for number of results
             *    and/or remove confidence for non-returning links. But it can be considered
             */
        }

        // Relevant heading element, scaled by heading size
        Elements relevantHeadings = page
                .select("h1:contains(" + DEPARTMENT_NAME + "), "
                        + "h2:contains(" + DEPARTMENT_NAME + "), "
                        + "h3:contains(" + DEPARTMENT_NAME + "), "
                        + "h4:contains(" + DEPARTMENT_NAME + "), "
                        + "h5:contains(" + DEPARTMENT_NAME + "), "
                        + "h6:contains(" + DEPARTMENT_NAME + ") ");
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
        String hostname = StringUtils.removeStart(URI.create(institution.website()).getHost(), "www.");

        // 1. Try some basics
        Document page = null;
        Document candidate = inPage;
        double candidateConfidence = foundDepartmentSite(candidate);

        for (String template : COMMON_TEMPLATES) {
            try {
                page = Jsoup.connect(String.format(template, hostname)).get();
            } catch (IOException e) {
                log.error("", e);
            }

            double confidence = foundDepartmentSite(page);
            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }
        }

        // 2. Actual crawling I guess?
        page = inPage;

        return page;
    }
}

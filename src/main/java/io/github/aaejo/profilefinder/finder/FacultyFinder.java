package io.github.aaejo.profilefinder.finder;

import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.profilefinder.finder.exception.FacultyListNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FacultyFinder {

    private final FinderClient client;

    public FacultyFinder(FinderClient client) {
        this.client = client;
    }

    public double foundFacultyList(Document page) {
        double confidence = 0;
        if (page == null) {
            log.info("0 confidence that faculty list found at null page");
            return 0;
        }

        try {
            int statusCode = page.connection().response().statusCode();
            if (statusCode >= 400) {
                log.info("0 confidence that faculty list found given HTTP {} status", statusCode);
                return 0;
            }
        } catch (IllegalArgumentException e) {
            // Protective case when trying to get response details before request has actually been made.
            // This is effectively only relevant when running from unit tests.
            log.debug("Cannot get connection response before request has been executed. This should only occur during tests.");
        }

        String location = page.location();
        String title = page.title();

        if (StringUtils.containsAnyIgnoreCase(title, "faculty", "staff", "employee", "directory", "people", "instructors")) {
            return 1;
        }

        return confidence;
    }

    public Document findFacultyList(Institution institution, Document inPage) {
        double confidence = foundFacultyList(inPage);
        return findFacultyList(institution, inPage, confidence);
    }

    public Document findFacultyList(Institution institution, Document inPage, double initialConfidence) {
        String possibleLinkSelector = "a[href]:contains(faculty)"; // TODO: This needs to be more flexible

        Elements possibleLinks = inPage.select(possibleLinkSelector);
        HashSet<String> checkedLinks = new HashSet<>(possibleLinks.size());

        Document candidate = inPage;
        double candidateConfidence = initialConfidence;

        for (int i = 0; i < possibleLinks.size(); i++) {
            String href = possibleLinks.get(i).attr("abs:href");

            if (checkedLinks.contains(href)) {
                continue; // Skip if this is a URL that has already been checked
            }

            Document page = client.get(href);

            double confidence = foundFacultyList(page);
            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }

            checkedLinks.add(href);
            Elements additionalLinks = page.select(possibleLinkSelector);
            possibleLinks.addAll(additionalLinks);
        }

        if (candidateConfidence < 1) {
            throw new FacultyListNotFoundException(institution, candidate.location(), candidateConfidence);
        }

        return inPage;
    }
}

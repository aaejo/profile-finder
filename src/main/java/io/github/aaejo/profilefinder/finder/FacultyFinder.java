package io.github.aaejo.profilefinder.finder;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.exception.FacultyListNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FacultyFinder {

    private final Connection session;

    public FacultyFinder(Connection session) {
        this.session = session;
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
            // Protective case when trying to get response details before request has
            // actually been made.
            // This is effectively only relevant when running from unit tests.
            log.debug(
                "Cannot get connection response before request has been executed. This should only occur during tests.");
            }

        String location = page.location();
        String title = page.title();

        if (StringUtils.containsAnyIgnoreCase(title, "faculty", "staff", "employee", "directory", "people", "instructors")) {
            return 1;
        }

        return confidence;
    }

    public Document findFacultyList(Institution institution, Document inPage) {
        Elements possibleLinks = inPage.select("a[href]:contains(faculty)");

        Document candidate = inPage;
        double candidateConfidence = foundFacultyList(candidate);

        for (Element link : possibleLinks) {
            Document page = null;
            try {
                page = session.newRequest().url(link.attr("abs:href")).get();
            } catch (IOException e) {
                log.error("", e);
            }

            double confidence = foundFacultyList(page);
            if (confidence >= 1) {
                return page;
            } else if (confidence > candidateConfidence) {
                candidateConfidence = confidence;
                candidate = page;
            }

            Elements additionalLinks = page.select("a[href]:contains(faculty)");
            possibleLinks.addAll(additionalLinks);
        }

        if (candidateConfidence < 1) {
            throw new FacultyListNotFoundException(institution, candidate.location(), candidateConfidence);
        }

        return inPage;
    }
}

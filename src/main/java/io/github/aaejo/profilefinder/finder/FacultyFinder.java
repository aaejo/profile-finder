package io.github.aaejo.profilefinder.finder;

import java.io.IOException;

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
public class FacultyFinder {


    public double foundFacultyList(Document page) {
        double confidence = 0;
        if (page == null) {
            log.info("0 confidence that faculty list found at null page");
            return 0;
        }

        String location = page.location();

        int statusCode = page.connection().response().statusCode();
        if (statusCode >= 400) {
            log.info("0 confidence that faculty list found given HTTP {} status", statusCode);
            return 0;
        }

        String title = page.title();
        // Faculty, staff, employee, directory, people...
        if (StringUtils.containsAnyIgnoreCase(title, "faculty", "staff", "employee", "directory", "people", "instructors")) {
            return 1;
        }

        return confidence;
    }

    public Document findFacultyList(Institution institution, Document inPage) {
        Elements possibleLinks = inPage.select("a[href]:contains(faculty)");

        Document page = null;
        Document candidate = inPage;
        double candidateConfidence = foundFacultyList(candidate);

        for (Element link : possibleLinks) {
            try {
                page = Jsoup.connect(link.attr("abs:href")).get();
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
        }

        return inPage;
    }
}

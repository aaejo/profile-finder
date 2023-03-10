package io.github.aaejo.profilefinder.messaging.consumer;

import java.util.Locale;

import org.jsoup.nodes.Document;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.profilefinder.finder.DepartmentFinder;
import io.github.aaejo.profilefinder.finder.FacultyFinder;
import io.github.aaejo.profilefinder.finder.ProfileFinder;
import io.github.aaejo.profilefinder.finder.exception.InstitutionLocaleInvalidException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@KafkaListener(id = "profile-finder", topics = "institutions")
public class InstitutionsListener {

    private final DepartmentFinder departmentFinder;
    private final FacultyFinder facultyFinder;
    private final ProfileFinder profileFinder;
    private final FinderClient client;

    public InstitutionsListener(DepartmentFinder departmentFinder, FacultyFinder facultyFinder,
            ProfileFinder profileFinder, FinderClient client) {
        this.departmentFinder = departmentFinder;
        this.facultyFinder = facultyFinder;
        this.profileFinder = profileFinder;
        this.client = client;
    }

    @KafkaHandler
    public void handle(Institution institution) {
        log.info("Processing {} ({})", institution.name(), institution.country());
        log.debug(institution.toString());

        Document page = client.get(institution.website());

        if (page == null) {
            log.error("Failed to load site for {}", institution.name());
            return;
        }

        // NOTE: Do we assume that no language is english? It's something that happens a lot
        Locale siteLocale = Locale.forLanguageTag(page.getElementsByTag("html").first().attr("lang"));
        if (!siteLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            log.error("Unable to process non-English websites. {} site's language is {}", institution.name(),
                    siteLocale.getDisplayLanguage());
            throw new InstitutionLocaleInvalidException(institution, siteLocale);
        }

        if (facultyFinder.foundFacultyList(page) < 1) { // Some institutions may already have the faculty page identified
            if (departmentFinder.foundDepartmentSite(page) < 1) { // Some institutions may already have the department page identified
                // Find department site
                page = departmentFinder.findDepartmentSite(institution, page);
            }
            // Find faculty list
            page = facultyFinder.findFacultyList(institution, page);
        }

        // Maybe try to find more accurate department mailing address in here somewhere?

        // Find profiles from faculty list
        profileFinder.findProfiles(institution, page);
    }

}

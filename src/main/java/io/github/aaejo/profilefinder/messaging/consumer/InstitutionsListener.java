package io.github.aaejo.profilefinder.messaging.consumer;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.finder.client.FinderClientResponse;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.DepartmentFinder;
import io.github.aaejo.profilefinder.finder.FacultyFinder;
import io.github.aaejo.profilefinder.finder.ProfileFinder;
import io.github.aaejo.profilefinder.finder.exception.InitialFetchFailedException;
import io.github.aaejo.profilefinder.finder.exception.InstitutionLocaleInvalidException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
@Component
@KafkaListener(id = "profile-finder", topics = "institutions")
public class InstitutionsListener {

    @Autowired
    KafkaTemplate<String, SimpleDebugData> debugTemplate;

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

        // Allow ignoring robots.txt rules on this one, because it's the initial page load.
        // This will either be the institution home page, or one that has been manually identified for use.
        FinderClientResponse page = client.get(institution.website(), false);

        if (page == null) {
            log.error("Failed to load site for {}", institution.name());
            throw new InitialFetchFailedException(institution);
        } else if (!page.isSuccess()) {
            log.error("Failed to load site for {}", institution.name());
            throw new InitialFetchFailedException(institution, page);
        }

        Locale siteLocale = Locale.forLanguageTag(page.document().getElementsByTag("html").first().attr("lang"));
        if (StringUtils.isNotBlank(siteLocale.getLanguage()) // Despite being required, sometimes a locale isn't set
                                                             // however we are only targeting primarily English-speaking
                                                             // countries and as such will assume an unset language
                                                             // is English.
                && !siteLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            // If the language is set and it's not English, we throw and skip this institution
            log.error("Unable to process non-English websites. {} site's language is {}", institution.name(),
                    siteLocale.getDisplayLanguage());
            throw new InstitutionLocaleInvalidException(institution, siteLocale);
        }

        double foundFacultyList = facultyFinder.foundFacultyList(page);
        if (foundFacultyList < 1.4) { // Some institutions may already have the faculty page identified
            double foundDepartmentSite = departmentFinder.foundDepartmentSite(page);
            if (foundDepartmentSite < 1.4) { // Some institutions may already have the department page identified
                // Find department site
                page = departmentFinder.findDepartmentSite(institution, page, foundDepartmentSite);
                // Re-calculate faculty list confidence because page changed
                foundFacultyList = facultyFinder.foundFacultyList(page);
            }

            if (departmentFinder.debugData == null) // Means the finder didn't run
                debugTemplate.send("department.debug", institution.name(), new SimpleDebugData(institution, page.location(), foundDepartmentSite));
            else
                departmentFinder.debugData = null;

            // Find faculty list
            page = facultyFinder.findFacultyList(institution, page, foundFacultyList);
        }

        // Maybe try to find more accurate department mailing address in here somewhere?

        if (facultyFinder.debugData == null) // Means the finder didn't run
            debugTemplate.send("faculty.debug", institution.name(), new SimpleDebugData(institution, page.location(), foundFacultyList));
        else
            facultyFinder.debugData = null;

        // Find profiles from faculty list
        profileFinder.findProfiles(institution, page);
        // TODO: Move this and the log after into ProfileFinder instead
        debugTemplate.send("profiles.debug", institution.name(), new SimpleDebugData(institution, page.location(), profileFinder.getFoundProfilesCount(institution)));
        log.info("{} (likely) profiles found for {}", profileFinder.getFoundProfilesCount(institution),
                institution.name());
    }

}

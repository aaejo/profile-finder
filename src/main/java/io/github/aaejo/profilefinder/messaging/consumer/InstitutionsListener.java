package io.github.aaejo.profilefinder.messaging.consumer;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.finder.DepartmentFinder;
import io.github.aaejo.profilefinder.finder.FacultyFinder;
import io.github.aaejo.profilefinder.finder.ProfileFinder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@KafkaListener(id = "profile-finder", topics = "institutions")
public class InstitutionsListener {

    private final DepartmentFinder departmentFinder;
    private final FacultyFinder facultyFinder;
    private final ProfileFinder profileFinder;

    public InstitutionsListener(DepartmentFinder departmentFinder, FacultyFinder facultyFinder, ProfileFinder profileFinder) {
        this.departmentFinder = departmentFinder;
        this.facultyFinder = facultyFinder;
        this.profileFinder = profileFinder;
    }

    @KafkaHandler
    public void handle(Institution institution) {
        log.info("Processing {}", institution.name());
        log.debug(institution.toString());

        // TODO: Retries
        Document page;
        try {
            page = Jsoup.connect(institution.website()).get();
        } catch (IOException e) {
            log.error("Failed to load site for {}", institution.name(), e);
            return;
        }

        // Maybe just move these checks (and the document loading?) into the find methods?
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

package io.github.aaejo.profilefinder.finder;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.messaging.producer.ProfilesProducer;

@Service
public class ProfileFinder {

    private final ProfilesProducer profilesProducer;

    public ProfileFinder(ProfilesProducer profilesProducer) {
        this.profilesProducer = profilesProducer;
    }

    public void findProfiles(Institution institution, Document facultyPage) {
        
    }
}

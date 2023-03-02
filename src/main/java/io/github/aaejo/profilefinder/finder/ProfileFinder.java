package io.github.aaejo.profilefinder.finder;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.messaging.producer.ProfilesProducer;

@Service
public class ProfileFinder {

    private final ProfilesProducer profilesProducer;
    private final Connection session;

    public ProfileFinder(ProfilesProducer profilesProducer, Connection session) {
        this.profilesProducer = profilesProducer;
        this.session = session;
    }

    public void findProfiles(Institution institution, Document facultyPage) {
        
    }
}

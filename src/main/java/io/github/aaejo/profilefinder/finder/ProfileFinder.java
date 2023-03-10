package io.github.aaejo.profilefinder.finder;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.profilefinder.messaging.producer.ProfilesProducer;

@Service
public class ProfileFinder {

    private final ProfilesProducer profilesProducer;
    private final FinderClient client;

    public ProfileFinder(ProfilesProducer profilesProducer, FinderClient client) {
        this.profilesProducer = profilesProducer;
        this.client = client;
    }

    public void findProfiles(Institution institution, Document facultyPage) {
        
    }
}

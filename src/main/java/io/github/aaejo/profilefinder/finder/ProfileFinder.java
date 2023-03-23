package io.github.aaejo.profilefinder.finder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.profilefinder.messaging.producer.ProfilesProducer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProfileFinder extends BaseFinder {

    private final ProfilesProducer profilesProducer;
    private final ObjectIntHashMap<String> stats;

    public ProfileFinder(ProfilesProducer profilesProducer, FinderClient client) {
        super(client);
        this.profilesProducer = profilesProducer;

        this.stats = ObjectIntHashMap.newMap();
    }

    public void findProfiles(Institution institution, Document facultyPage) {
        stats.put(institution.name(), 0);
        Element content = drillDownToContent(facultyPage);
        boolean hasNextPage = false;

        do {
            Elements contentElements = content.getAllElements();
            Map<Element, Elements> parentToChildren = new HashMap<>(contentElements.size());

            for (Element element : contentElements) {
                parentToChildren.put(element, element.children());
            }

            Entry<Element, Elements> mostChildrenEntry = parentToChildren.entrySet().stream()
                    .max((o1, o2) -> Integer.compare(o1.getValue().size(), o2.getValue().size())).get();

            ObjectIntHashMap<String> tagFrequency = ObjectIntHashMap.newMap();
            for (Element element : mostChildrenEntry.getValue()) {
                tagFrequency.addToValue(element.tagName(), 1);
            }

            String commonTag = tagFrequency.keyValuesView()
                    .max((tag1, tag2) -> Integer.compare(tag1.getTwo(), tag2.getTwo())).getOne();

            Elements commonTagChildren = new Elements(mostChildrenEntry.getValue());
            commonTagChildren.removeIf(e -> !e.tagName().equals(commonTag));

            for (Element element : commonTagChildren) {
                if (StringUtils.isEmpty(element.text())){
                    log.debug("Skipping element without text content: {}", element);
                    continue;
                }
                Element link = element.selectFirst("a[href]:not([href^=mailto:]):not([href^=tel])");
                String url = link != null ? link.absUrl("href") : StringUtils.EMPTY;
                Profile profile = new Profile(element.outerHtml(), url, null, institution);
                profilesProducer.send(profile);
                stats.addToValue(institution.name(), 1);
            }

            // TODO: As much as I love this solution, it fails if there are separate sections of profile details (e.g. MIT)
            // In this case it only finds the largest one. Might need to revisit this.
            // Also fails if each entry isn't its own element (e.g. ubishops)

            // FIXME: This feels hacky
            Element nextPageControl = content.selectFirst("a[href]:contains(next)");
            if (nextPageControl != null) {
                hasNextPage = true;
                Document nextPage = client.get(nextPageControl.absUrl("href"));
                content = drillDownToContent(nextPage);
            } else {
                hasNextPage = false;
            }
        } while (hasNextPage);
    }

    public int getFoundProfilesCount(String institutionName) {
        return stats.get(institutionName);
    }

    public int getFoundProfilesCount(Institution institution) {
        return stats.get(institution.name());
    }
}

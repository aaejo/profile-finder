package io.github.aaejo.profilefinder.finder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.map.primitive.ObjectDoubleMap;
import org.eclipse.collections.api.tuple.primitive.ObjectDoublePair;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import io.github.aaejo.finder.client.FinderClient;
import io.github.aaejo.messaging.records.Institution;
import io.github.aaejo.messaging.records.Profile;
import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.github.aaejo.profilefinder.messaging.producer.ProfilesProducer;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
@Service
public class ProfileFinder extends BaseFinder {

    private final ProfilesProducer profilesProducer;
    private final DepartmentFinder departmentFinder;
    private final ObjectIntHashMap<String> stats;

    public ProfileFinder(ProfilesProducer profilesProducer, DepartmentFinder departmentFinder, FinderClient client,
            CrawlingProperties properties) {
        super(client, properties);
        this.profilesProducer = profilesProducer;
        this.departmentFinder = departmentFinder;

        this.stats = ObjectIntHashMap.newMap();
    }

    enum StrategyCondition {
        DEPARTMENT_SPECIFIC,
        DEPARTMENT_CONTAINS,
        DEPARTMENT_UNKNOWN,
        SINGLE_LIST,
        SINGLE_TABLE
    }

    enum Strategy {
        COMMON_TAG, SINGLE_LIST, SINGLE_TABLE, SINGLE_LIST_CAREFUL, SINGLE_TABLE_CAREFUL
    }

    public void findProfiles(Institution institution, final Document facultyPage) {
        stats.put(institution.name(), 0);
        Element content = drillDownToUniqueMain(facultyPage).get(0);
        boolean hasNextPage = false;
        EnumSet<StrategyCondition> strategyConditions = EnumSet.noneOf(StrategyCondition.class);
        Strategy strategy = Strategy.COMMON_TAG;

        // Check whether this is a department-specific list or a general one
        // If it's a general one, need to take special precautions
        ObjectDoubleMap<DepartmentKeyword> departmentReport = departmentFinder.foundDepartmentSiteDetailed(facultyPage);
        DepartmentKeyword highestDetected = departmentReport.keyValuesView().maxBy(ObjectDoublePair::getTwo).getOne();
        DepartmentKeyword primary = departmentFinder.getPrimaryDepartment();

        // TODO: Might want to check variances rather than absolute highest?
        if (highestDetected.isPrimary() && departmentReport.get(primary) >= 1.0) {
            // We're on a department-specific list
            strategyConditions.add(StrategyCondition.DEPARTMENT_SPECIFIC);
        } else {
            // We're not
            if (highestDetected.getWeight() > 0.01) {
                strategyConditions.add(StrategyCondition.DEPARTMENT_CONTAINS);
            } else {
                strategyConditions.add(StrategyCondition.DEPARTMENT_UNKNOWN);
            }

            // Look for headers
            Elements primaryHeadings = content.select(primary.getRelevantHeading());
            Element sectionHeading = null;
            for (Element pHeading : primaryHeadings) {
                if (pHeading.siblingElements().stream()
                        .anyMatch(e -> !e.equals(pHeading) && e.tag().equals(pHeading.tag()))) {
                    sectionHeading = pHeading;
                    break;
                }
            }
            if (sectionHeading != null) {
                List<Element> sectionContents = new ArrayList<>();
                // Walk through all sibling elements following sectionHeading until we run out
                // or hit another heading of the same level
                for (Element sib : sectionHeading.nextElementSiblings()) {
                    if (!sib.tag().equals(sectionHeading.tag())) {
                        sectionContents.add(sib);
                    } else {
                        // Another heading of the same type means we've hit another section
                        break;
                    }
                }
            }

            // Results will need to be checked for indicators of relevance
        }

        // Look for separators!!

        List<Element> wellNamedItems = content
                .select("[id*=contact], [id*=bio], [id*=person], [id*=staff], [id*=faculty], [id*=instructors], [id*=people], "
                        + "[class*=contact], [class*=bio], [class*=person], [class*=staff], [class*=faculty], [class*=instructors], [class*=people]")
                .stream()
                .distinct()
                .filter(e -> e.tag().isBlock())
                .toList();

        if (wellNamedItems.size() == 1) {
            // If there's only 1, then it's probably a parent of what we want
            // Maybe let's commonTagStrategy it?
        } else {
            // Fair chance we found what we need
        }

        Elements unorderedLists = content.getElementsByTag("ul");
        if (unorderedLists.size() == 1) {
            strategyConditions.add(StrategyCondition.SINGLE_LIST);
        } else {
            List<Element> relevantUnorderedLists = new ArrayList<Element>();
            for (Element ul : unorderedLists) {
                if (StringUtils.containsAny(ul.id(), "staff", "faculty", "instructors", "people")
                        || StringUtils.containsAny(ul.className(), "staff", "faculty", "instructors", "people")) {
                    relevantUnorderedLists.add(ul);
                    unorderedLists.remove(ul);
                } 
            }
        }

        Elements orderedLists = content.getElementsByTag("ol");

        Elements tables = content.getElementsByTag("table");
        if (tables.size() == 1) {
            // 1 big table? Probably what we're looking for
            strategyConditions.add(StrategyCondition.SINGLE_TABLE);
        } else {
            // ... I dunno?
        }

        if (strategyConditions.contains(StrategyCondition.DEPARTMENT_SPECIFIC)) {
            if (false) {

            } else if (strategyConditions.contains(StrategyCondition.SINGLE_LIST)) {
                strategy = Strategy.SINGLE_LIST;
            } else if (strategyConditions.contains(StrategyCondition.SINGLE_TABLE)) {
                strategy = Strategy.SINGLE_TABLE;
            }
        } else {
            if (strategyConditions.contains(StrategyCondition.SINGLE_LIST)) {
                strategy = Strategy.SINGLE_LIST_CAREFUL;
            } else if (strategyConditions.contains(StrategyCondition.SINGLE_TABLE)) {
                strategy = Strategy.SINGLE_TABLE_CAREFUL;
            }
        }

        // do {
            List<Element> facultyListElements = switch (strategy) {
                case COMMON_TAG -> commonTagStrategy(content);
                case SINGLE_LIST -> singleListStrategy(unorderedLists.first());
                case SINGLE_LIST_CAREFUL -> careful(singleListStrategy(unorderedLists.first()));
                case SINGLE_TABLE -> singleTableStrategy(tables.first());
                case SINGLE_TABLE_CAREFUL -> careful(singleTableStrategy(tables.first()));
                default -> throw new IllegalArgumentException("Unexpected value: " + strategy);
            };
            for (Element element : facultyListElements) {
                if (element.text().split(" ", 2).length < 2
                    && element.selectFirst("a[href]") == null) {
                    log.debug("Skipping element without text content: {}", element);
                    continue;
                } 

                Element link = element.selectFirst("a[href]:not([href^=mailto:]):not([href^=tel])");
                String url = link != null ? link.absUrl("href") : StringUtils.EMPTY;
                Institution newInstitution = new Institution(institution.name(), institution.country(),
                        institution.address(), facultyPage.location());
                Profile profile = new Profile(element.outerHtml(), url, null, newInstitution);
                profilesProducer.send(profile);
                stats.addToValue(institution.name(), 1);
            }

            // FIXME: This feels hacky. It's not really, but it feels like it
            Element nextPageControl = content.selectFirst("a[href]:contains(next)");
            if (nextPageControl != null) {
                hasNextPage = true;
                Document nextPage = client.get(nextPageControl.absUrl("href"));
                content = drillDownToUniqueMain(nextPage).get(0);
            } else {
                hasNextPage = false;
            }
        // } while (hasNextPage);
    }

    // private Strategy selectStrategy(final Document facultyPage, Element content) {
        
    // }

    private List<Element> commonTagStrategy(Element content) {
        Elements contentElements = content.getAllElements();
        Map<Element, List<Element>> parentToBlockChildren = new HashMap<>(contentElements.size());

        for (Element element : contentElements) {
            List<Element> blockLevelChildren = element.children().stream().filter(c -> c.tag().isBlock()).toList();
            parentToBlockChildren.put(element, blockLevelChildren);
        }

        Entry<Element, List<Element>> mostChildrenEntry = parentToBlockChildren.entrySet().stream()
                .max((o1, o2) -> Integer.compare(o1.getValue().size(), o2.getValue().size())).get();

        ObjectIntHashMap<String> tagFrequency = ObjectIntHashMap.newMap();
        for (Element element : mostChildrenEntry.getValue()) {
            tagFrequency.addToValue(element.tagName(), 1);
        }

        String commonTag = tagFrequency.keyValuesView()
                .max((tag1, tag2) -> Integer.compare(tag1.getTwo(), tag2.getTwo())).getOne();

        List<Element> commonTagChildren = new ArrayList<Element>(mostChildrenEntry.getValue());
        commonTagChildren.removeIf(e -> !e.tagName().equals(commonTag));
        return commonTagChildren;

        // TODO: As much as I love this solution, it fails if there are separate sections of profile details (e.g. MIT)
        // In this case it only finds the largest one. Might need to revisit this.
        // Also fails if each entry isn't its own element (e.g. ubishops)
    }

    private List<Element> singleListStrategy(Element list) {
        if (!StringUtils.equalsAny(list.tagName(), "ul", "ol")) {
            return List.of();
        }

        return new ArrayList<>(list.children());
    }

    private List<Element> singleTableStrategy(Element table) {
        if (!StringUtils.equals(table.tagName(), "table")) {
            return List.of();
        }
        table = table.getElementsByTag("tbody").first() != null ? table.getElementsByTag("tbody").first() : table;

        return new ArrayList<>(table.getElementsByTag("tr"));
    }

    private List<Element> careful(List<Element> elements) {
        return elements.stream()
                .filter(e -> StringUtils.containsAnyIgnoreCase(e.text(), departmentFinder.getRelevantDepartmentVariants()))
                .toList();
    }

    public int getFoundProfilesCount(String institutionName) {
        return stats.get(institutionName);
    }

    public int getFoundProfilesCount(Institution institution) {
        return stats.get(institution.name());
    }
}

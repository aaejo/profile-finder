package io.github.aaejo.profilefinder.finder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

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

    enum DepartmentSpecificity {
        DEPARTMENT_SPECIFIC,
        DEPARTMENT_CONTAINS,
        DEPARTMENT_UNKNOWN
    }

    enum StrategyCondition {
        IDEAL_COUNT,
        IMAGES,
        SINGLE_WELL_NAMED,
        SINGLE_LIST,
        SINGLE_TABLE,
        WELL_NAMED,
        VERY_WELL_NAMED, SEPARATORS, RELEVANT_LISTS, DEPARTMENT_SPECIFIC_SUBSECTION
    }

    public void findProfiles(Institution institution, final Document facultyPage) {
        log.info("Extracting profiles from {}", facultyPage.location());

        stats.put(institution.name(), 0);
        Element content = drillDownToUniqueMain(facultyPage).get(0);
        boolean hasNextPage = false;
        EnumSet<StrategyCondition> strategyConditions = EnumSet.noneOf(StrategyCondition.class);

        // Check whether this is a department-specific list or a general one
        // If it's a general one, need to take special precautions
        ObjectDoubleMap<DepartmentKeyword> departmentReport = departmentFinder.foundDepartmentSiteDetailed(facultyPage);
        DepartmentKeyword highestDetected = departmentReport.keyValuesView().maxBy(ObjectDoublePair::getTwo).getOne();
        // DepartmentKeyword lowestDetected = departmentReport.keyValuesView().minBy(ObjectDoublePair::getTwo).getOne();

        DepartmentSpecificity specificity;
        if (departmentReport.get(highestDetected) < 0.1) {
            specificity = DepartmentSpecificity.DEPARTMENT_UNKNOWN;
        } else {
            if (highestDetected.isPrimary()) {
                if (departmentReport.get(highestDetected) == departmentReport.sum()) { // ie rest are all 0
                    specificity = DepartmentSpecificity.DEPARTMENT_SPECIFIC;
                } else if (departmentReport.get(highestDetected) < 1.0) {
                    specificity = DepartmentSpecificity.DEPARTMENT_UNKNOWN;
                } else {
                    specificity = DepartmentSpecificity.DEPARTMENT_SPECIFIC;
                }
            } else if (departmentReport.get(highestDetected) < 1.0) {
                specificity = DepartmentSpecificity.DEPARTMENT_UNKNOWN;
            } else {
                if (departmentReport.get(departmentFinder.getPrimaryDepartment()) >= 1.0) {
                    if (departmentReport.get(highestDetected) >= 1.4) {
                        specificity = DepartmentSpecificity.DEPARTMENT_CONTAINS;
                    } else {
                        specificity = DepartmentSpecificity.DEPARTMENT_SPECIFIC;
                    }
                } else {
                    specificity = DepartmentSpecificity.DEPARTMENT_CONTAINS;
                }
            }
        }

        do {
            List<Element> sectionContents = new ArrayList<>();
            if (specificity != DepartmentSpecificity.DEPARTMENT_SPECIFIC) {
                // Look for headers
                Elements primaryHeadings = content.select(departmentFinder.getPrimaryDepartment().getRelevantHeading());
                Element sectionHeading = null;
                for (Element pHeading : primaryHeadings) {
                    if (pHeading.siblingElements().stream()
                            .anyMatch(e -> !e.equals(pHeading) && e.tag().equals(pHeading.tag()))) {
                        sectionHeading = pHeading;
                        break;
                    }
                }
                if (sectionHeading != null) {
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
                    strategyConditions.add(StrategyCondition.DEPARTMENT_SPECIFIC_SUBSECTION);
                }
            }

            Elements images = content.getElementsByTag("img");
            if (!images.isEmpty()) {
                strategyConditions.add(StrategyCondition.IMAGES);
            }

            List<Element> veryWellNamedItems = content
                    .select(".contact-card, .person, .profile, .staff-card, .staff-listing")
                    .stream()
                    .distinct()
                    .filter(e -> e.tag().isBlock())
                    .toList();

            if (!veryWellNamedItems.isEmpty()) {
                strategyConditions.add(StrategyCondition.VERY_WELL_NAMED);

                boolean eachHasExactlyOneImage = true;
                boolean eachHasExactlyOneEmailLink = true;
                boolean eachHasExactlyOneUniqueLink = true;

                for (Element item : veryWellNamedItems) {
                    eachHasExactlyOneImage &= item.getElementsByTag("img").size() == 1;
                    eachHasExactlyOneEmailLink &= item.select("a[href^=mailto:]").size() == 1;
                    // Sometimes there are multiple links, but they all go to the same page
                    eachHasExactlyOneUniqueLink &= item.select("a[href]:not([href^=mailto:]):not([href^=tel])")
                            .eachAttr("abs:href").stream().distinct().count() == 1;
                }

                if (eachHasExactlyOneImage || eachHasExactlyOneEmailLink || eachHasExactlyOneUniqueLink) {
                    strategyConditions.add(StrategyCondition.IDEAL_COUNT);
                }
            }

            List<Element> wellNamedItems = content
                    .select("[id*=contact], [id*=bio], [id*=person], [id*=staff], [id*=faculty], [id*=instructors], [id*=people], "
                            + "[class*=contact], [class*=bio], [class*=person], [class*=staff], [class*=faculty], [class*=instructors], [class*=people]")
                    .stream()
                    .distinct()
                    .filter(e -> e.tag().isBlock())
                    .toList();

            if (!wellNamedItems.isEmpty()) {
                if (wellNamedItems.size() == 1) {
                    // If there's only 1, then it's probably a parent of what we want
                    // Maybe let's commonTagStrategy it?
                    strategyConditions.add(StrategyCondition.SINGLE_WELL_NAMED);
                } else if (strategyConditions.contains(StrategyCondition.IMAGES)
                        && wellNamedItems.size() == images.size()) {
                    strategyConditions.add(StrategyCondition.IDEAL_COUNT);
                    strategyConditions.add(StrategyCondition.WELL_NAMED);
                } else if (strategyConditions.contains(StrategyCondition.IMAGES)
                        && wellNamedItems.size() % images.size() == 0) {
                    //
                } else {
                    // Fair chance we found what we need
                }
            }

            Elements unorderedLists = content.getElementsByTag("ul");
            if (!unorderedLists.isEmpty()) {
                if (unorderedLists.size() == 1) {
                    if (!StringUtils.containsAnyIgnoreCase(unorderedLists.first().id(), "page", "pagination")
                            && !StringUtils.containsAnyIgnoreCase(unorderedLists.first().className(), "page",
                                    "pagination")) {
                        strategyConditions.add(StrategyCondition.SINGLE_LIST);
                    }
                } else {
                    List<Element> relevantUnorderedLists = new ArrayList<Element>();
                    for (Element ul : unorderedLists) {
                        if (StringUtils.containsAny(ul.id(), "staff", "faculty", "instructors", "people")
                                || StringUtils.containsAny(ul.className(), "staff", "faculty", "instructors",
                                        "people")) {
                            relevantUnorderedLists.add(ul);
                            unorderedLists.remove(ul);
                        }
                    }

                    if (!relevantUnorderedLists.isEmpty()) {
                        if (relevantUnorderedLists.size() == 1) {
                            strategyConditions.add(StrategyCondition.SINGLE_LIST);
                        } else {
                            strategyConditions.add(StrategyCondition.RELEVANT_LISTS);
                        }
                        unorderedLists = (Elements) relevantUnorderedLists;
                    }
                }
            }

            Elements tables = content.getElementsByTag("table");
            if (tables.size() == 1) {
                // 1 big table? Probably what we're looking for
                strategyConditions.add(StrategyCondition.SINGLE_TABLE);
            } else {
                // ... I dunno?
            }

            List<Element> separators = content
                    .select("hr, [class*=spacer], [class*=separator]")
                    .stream()
                    .distinct()
                    .toList();

            List<Element> scope = null;
            DepartmentSpecificity scopedSpecificity = specificity; // Sometimes our scope will be more specific than the
                                                                   // page
            Function<Element, List<Element>> strategyFunction;

            if (strategyConditions.contains(StrategyCondition.DEPARTMENT_SPECIFIC_SUBSECTION)) {
                scope = sectionContents;
                scopedSpecificity = DepartmentSpecificity.DEPARTMENT_SPECIFIC;
                strategyFunction = this::subsectionStrategy;
            } else if (strategyConditions.contains(StrategyCondition.IDEAL_COUNT)
                    && strategyConditions.contains(StrategyCondition.VERY_WELL_NAMED)) {
                scope = veryWellNamedItems;
                strategyFunction = List::of;
            } else if (strategyConditions.contains(StrategyCondition.IDEAL_COUNT)
                    && strategyConditions.contains(StrategyCondition.WELL_NAMED)) {
                scope = wellNamedItems;
                strategyFunction = List::of;
            } else if (strategyConditions.contains(StrategyCondition.SINGLE_WELL_NAMED)) {
                scope = List.of(wellNamedItems.get(0));
                strategyFunction = this::commonTagStrategy;
            } else if (strategyConditions.contains(StrategyCondition.SINGLE_LIST)) {
                scope = List.of(unorderedLists.first());
                strategyFunction = this::singleListStrategy;
            } else if (strategyConditions.contains(StrategyCondition.SINGLE_TABLE)) {
                scope = List.of(tables.first());
                strategyFunction = this::singleTableStrategy;
            } else if (strategyConditions.contains(StrategyCondition.SEPARATORS)) {
                strategyFunction = null;
            } else if (strategyConditions.contains(StrategyCondition.VERY_WELL_NAMED)) {
                // If nothing else worked, and we have some very well named items, we'll use
                // them
                // even if the count doesn't seem ideal?
                scope = veryWellNamedItems;
                strategyFunction = List::of;
            } else {
                scope = List.of(content);
                strategyFunction = this::commonTagStrategy;
            }

            List<Element> facultyListElements = applyStrategy(scope, scopedSpecificity, strategyFunction);
            for (Element element : facultyListElements) {
                if (element.text().split(" ", 2).length < 2
                    && element.selectFirst("a[href]") == null) {
                    log.debug("Skipping element with insufficient text content: {}", element);
                    continue;
                } 

                Element link = element.selectFirst("a[href]:not([href^=mailto:]):not([href^=tel])");
                String url = link != null ? link.absUrl("href") : StringUtils.EMPTY;
                // Since institution.website no longer used in the rest of the pipeline, using it for htmlContent base url
                Institution newInstitution = new Institution(institution.name(), institution.country(),
                        institution.address(), facultyPage.location());
                Profile profile = new Profile(element.outerHtml(), url, null, newInstitution);
                profilesProducer.send(profile);
                stats.addToValue(institution.name(), 1);
            }

            // FIXME: This feels hacky. It's not really, but it feels like it
            // Sometimes pagination isn't actually handled at the URL level, it's purely
            // dynamic. Maybe we need a special method in FinderClient for that.
            Element nextPageControl = content.selectFirst("a[href^=http]:contains(next)");
            if (nextPageControl != null && nextPageControl.absUrl("href") != null) {
                hasNextPage = true;
                Document nextPage = client.get(nextPageControl.absUrl("href"));
                content = drillDownToUniqueMain(nextPage).get(0);
            } else {
                hasNextPage = false;
            }
        } while (hasNextPage);
    }

    private List<Element> applyStrategy(List<Element> scope, DepartmentSpecificity specificity, Function<Element, List<Element>> strategy) {
        List<Element> results = new ArrayList<>();
        for (Element scopeItem : scope) {
            results.addAll(strategy.apply(scopeItem));
        }
        
        return switch (specificity) {
            case DEPARTMENT_SPECIFIC -> results;
            case DEPARTMENT_CONTAINS -> veryCareful(results);
            case DEPARTMENT_UNKNOWN  -> careful(results);
        };
    }

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

    private List<Element> subsectionStrategy(Element element) {
        return switch (element.tagName()) {
            case "ul" -> singleListStrategy(element);
            case "table" -> singleTableStrategy(element);
            default -> commonTagStrategy(element);
        };
    }

    private List<Element> careful(List<Element> elements) {
        return elements.stream()
        .filter(e -> StringUtils.containsAnyIgnoreCase(e.text(), departmentFinder.getImportantDepartmentVariants()))
        .toList();
    }

    private List<Element> veryCareful(List<Element> elements) {
        return elements.stream()
        .filter(e -> StringUtils.containsAnyIgnoreCase(e.text(), departmentFinder.getPrimaryDepartment().getVariants()))
        .toList();
    }

    public int getFoundProfilesCount(String institutionName) {
        return stats.get(institutionName);
    }

    public int getFoundProfilesCount(Institution institution) {
        return stats.get(institution.name());
    }
}

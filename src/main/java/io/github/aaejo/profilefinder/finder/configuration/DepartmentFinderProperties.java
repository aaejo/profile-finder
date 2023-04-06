package io.github.aaejo.profilefinder.finder.configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.aaejo.profilefinder.finder.DepartmentKeyword;

/**
 * @author Omri Harary
 */
@ConfigurationProperties("aaejo.jds.department-finder")
public class DepartmentFinderProperties {

    private final List<String> templates;
    private final List<DepartmentKeyword> keywords;
    private final DepartmentKeyword primary;

    public DepartmentFinderProperties(List<String> templates, List<DepartmentKeyword> keywords) {
        this.templates = templates;
        this.keywords = keywords;
        primary = keywords.stream().filter(DepartmentKeyword::isPrimary).findFirst().get();
        Collections.sort(keywords);
    }

    public List<DepartmentKeyword> getImportantDepartmentKeywords() {
        return keywords.stream().filter(kw -> kw.getWeight() > 0.01).toList();
    }

    public String[] getImportantDepartmentVariants() {
        return getImportantDepartmentKeywords().stream()
                .flatMap(kw -> Arrays.stream(kw.getVariants()))
                .toArray(String[]::new);
    }

    /**
     * @return the templates
     */
    public List<String> getTemplates() {
        return templates;
    }

    /**
     * @return the keywords
     */
    public List<DepartmentKeyword> getKeywords() {
        return keywords;
    }

    /**
     * @return the primary
     */
    public DepartmentKeyword getPrimary() {
        return primary;
    }
}

package io.github.aaejo.profilefinder.finder.configuration;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.aaejo.profilefinder.finder.DepartmentKeyword;

@ConfigurationProperties("aaejo.jds.department-finder")
public record DepartmentFinderProperties(List<String> templates, List<DepartmentKeyword> keywords) {
}

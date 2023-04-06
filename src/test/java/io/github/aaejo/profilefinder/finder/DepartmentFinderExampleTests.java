package io.github.aaejo.profilefinder.finder;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import io.github.aaejo.profilefinder.finder.configuration.CrawlingProperties;
import io.github.aaejo.profilefinder.finder.configuration.DepartmentFinderProperties;

/*
 * Tests for DepartmentFinder utilizing actual static HTML
 */
public class DepartmentFinderExampleTests {

    List<String> commonTemplates = List.of(
            "https://%s/philosophy",
            "https://philosophy.%s",
            "https://%s/department/philosophy",
            "https://%s/dept/philosophy",
            "https://%s/artsci/philosophy",
            "https://%s/humanities/philosophy",
            "https://phil.%s"
        );
    List<DepartmentKeyword> departmentKeywords = new ArrayList<>(List.of(
            new DepartmentKeyword(new String[]{"philosophy"}, 1.0, true),
            new DepartmentKeyword(new String[]{"humanities"}, 0.8, false),
            new DepartmentKeyword(new String[]{"social science", "social-science", "socialscience"}, 0.8, false)));
    DepartmentFinderProperties dfProps = new DepartmentFinderProperties(commonTemplates, departmentKeywords);
    
    String[] disallowedHosts = { "outlook.com", "sharepoint.com" };
    CrawlingProperties cProps = new CrawlingProperties(false, 0.0001, disallowedHosts);
    private final DepartmentFinder departmentFinder = new DepartmentFinder(null, dfProps, cProps);

    @Test
    void foundDepartmentSite_queensPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/queens-philosophy.html"),
                "UTF-8",
                "https://www.queensu.ca/philosophy/");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }

    /**
     * Relevant because of lack of "Department" in title and some other contexts
     */
    @Test
    void foundDepartmentSite_mitPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/mit-philosophy.html"),
                "UTF-8",
                "https://philosophy.mit.edu/");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void foundDepartmentSite_harvardPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/harvard-philosophy.html"),
                "UTF-8",
                "https://philosophy.fas.harvard.edu/");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void foundDepartmentSite_berkeleyPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/berkeley-philosophy.html"),
                "UTF-8",
                "https://philosophy.berkeley.edu/");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void foundDepartmentSite_uahPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/uah-philosophy.html"),
                "UTF-8",
                "https://www.uah.edu/ahs/departments/philosophy");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }

    /**
     * This is the Philosophy Major, B.A. Degree page at Nazareth, which is the page that gets directed to by
     * one of the "common templates" that the finder tries to use. It is not the department website, rather
     * it is an informational page about the philosophy major.
     */
    @Test
    void foundDepartmentSite_nazarethPhilosophyMajor_isNotDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/nazereth-philosophy-major.html"),
                "UTF-8",
                "https://www2.naz.edu/academics/philosophy-major/");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isLessThan(1.0);
    }

    @Test
    void foundDepartmentSite_nazarethPhilosophy_isDepartmentSite() throws IOException {
        Document departmentPage = Jsoup.parse(
                new File("src/test/resources/department-examples/nazereth-philosophy.html"),
                "UTF-8",
                "https://www2.naz.edu/dept/philosophy");

        double confidence = departmentFinder.foundDepartmentSite(departmentPage);

        System.out.println("Confidence = " + confidence);
        assertThat(confidence).isGreaterThanOrEqualTo(1.0);
    }
}

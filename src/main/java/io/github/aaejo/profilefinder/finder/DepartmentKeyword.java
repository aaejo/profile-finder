package io.github.aaejo.profilefinder.finder;

import java.util.Comparator;

import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @author Omri Harary
 */
public class DepartmentKeyword implements Comparable<DepartmentKeyword> {
    public static final DepartmentKeyword UNDEFINED = new DepartmentKeyword(new String[]{"undefined"}, Double.NaN, false);

    private final String[] variants;
    private final double weight;
    private final boolean primary;
    private final Evaluator relevantImageLink;
    private final Evaluator relevantLink;
    private final Evaluator relevantHeading;

    public DepartmentKeyword(String[] variants, double weight, @DefaultValue("false") boolean primary) {
        this.variants = variants;
        this.weight = weight;
        this.primary = primary;

        String relevantImageLinkQuery = "a[href] img[alt*=" + variants[0] + "]";
        String relevantLinkQuery = "a[href]:contains(" + variants[0] + ")";
        String relevantHeadingQuery = "h1:contains(" + variants[0] + "), "
                                    + "h2:contains(" + variants[0] + "), "
                                    + "h3:contains(" + variants[0] + "), "
                                    + "h4:contains(" + variants[0] + "), "
                                    + "h5:contains(" + variants[0] + "), "
                                    + "h6:contains(" + variants[0] + ") ";
        if (variants.length > 1) {
            for (int i = 1; i < variants.length; i++) {
                relevantImageLinkQuery += ", a[href] img[alt*=" + variants[i] + "]";
                relevantLinkQuery += ", a[href]:contains(" + variants[i] + ")";
                relevantHeadingQuery += ", h1:contains(" + variants[i] + "), "
                                        + "h2:contains(" + variants[i] + "), "
                                        + "h3:contains(" + variants[i] + "), "
                                        + "h4:contains(" + variants[i] + "), "
                                        + "h5:contains(" + variants[i] + "), "
                                        + "h6:contains(" + variants[i] + ") ";
            }
        }
        this.relevantImageLink = QueryParser.parse(relevantImageLinkQuery);
        this.relevantLink = QueryParser.parse(relevantLinkQuery);
        this.relevantHeading = QueryParser.parse(relevantHeadingQuery);
    }

    /**
     * @return the variants
     */
    public String[] getVariants() {
        return variants;
    }

    /**
     * @return the weight
     */
    public double getWeight() {
        return weight;
    }

    public boolean isPrimary() {
        return primary;
    }

    /**
     * @return the relevantImageLink
     */
    public Evaluator getRelevantImageLink() {
        return relevantImageLink;
    }

    /**
     * @return the relevantLink
     */
    public Evaluator getRelevantLink() {
        return relevantLink;
    }

    /**
     * @return the relevantHeading
     */
    public Evaluator getRelevantHeading() {
        return relevantHeading;
    }

    @Override
    public int compareTo(DepartmentKeyword o) {
        return Comparator
                .comparing(DepartmentKeyword::isPrimary) // Sort by primary-ness first
                .thenComparing(Comparator.comparing(DepartmentKeyword::getWeight)) // Then by weight
                .reversed() // In descending order
                .compare(this, o);
    }
}

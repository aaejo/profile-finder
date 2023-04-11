package io.github.aaejo.profilefinder.finder;

import java.util.Comparator;
import java.util.regex.Pattern;

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
    private final Pattern variantsRegex;
    private final Evaluator relevantImageLink;
    private final Evaluator relevantLink;
    private final Evaluator relevantHeading;

    public DepartmentKeyword(String[] variants, double weight, @DefaultValue("false") boolean primary) {
        this.variants = variants;
        this.weight = weight;
        this.primary = primary;

        String regex = "(?iu:\\b" + Pattern.quote(variants[0]) + "\\b";
        String relevantImageLinkQuery = "a[href] img[alt*=" + variants[0] + "]";
        String relevantLinkQuery = "a[href]:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b)";
        String relevantHeadingQuery = "h1:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b), "
                                    + "h2:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b), "
                                    + "h3:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b), "
                                    + "h4:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b), "
                                    + "h5:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b), "
                                    + "h6:matches((?iu)\\b" + Pattern.quote(variants[0]) + "\\b) ";
        if (variants.length > 1) {
            for (int i = 1; i < variants.length; i++) {
                regex += "|\\b" + Pattern.quote(variants[i]) + "\\b";
                relevantImageLinkQuery += ", a[href] img[alt*=" + variants[i] + "]";
                relevantLinkQuery += ", a[href]:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b)";
                relevantHeadingQuery += ", h1:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b), "
                                        + "h2:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b), "
                                        + "h3:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b), "
                                        + "h4:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b), "
                                        + "h5:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b), "
                                        + "h6:matches((?iu)\\b" + Pattern.quote(variants[i]) + "\\b) ";
            }
        }
        regex += ")"; // make sure to close the group
    
        this.variantsRegex = Pattern.compile(regex);
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
     * @return the variantsRegex
     */
    public Pattern getVariantsRegex() {
        return variantsRegex;
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

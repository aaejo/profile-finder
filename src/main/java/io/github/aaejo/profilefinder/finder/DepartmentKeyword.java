package io.github.aaejo.profilefinder.finder;

import org.jsoup.select.Evaluator;
import org.jsoup.select.QueryParser;

public class DepartmentKeyword {
    private String[] variants;
    private double weight;
    private Evaluator relevantImageLink;
    private Evaluator relevantLink;
    private Evaluator relevantHeading;

    public DepartmentKeyword(double weight, String[] variants) {
        this.variants = variants;
        this.weight = weight;

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

    /**
     * @param variants the variants to set
     */
    public void setVariants(String[] variants) {
        this.variants = variants;
    }

    /**
     * @param weight the weight to set
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }
}

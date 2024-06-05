package io.jenkins.plugins.forensics.git;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the analysis summary on the build page of a job.
 *
 * @author Ullrich Hafner
 * @author Manuel Hampp
 * @author Michaela Reitschuster
 * @author Alexandra Wenzel
 */
public class Summary extends PageObject {
    private static final Pattern REMOVE_DETAILS = Pattern.compile("(\\r?\\n|\\r).*");

    private final WebElement summarySpan;
    private final String title;
    private final List<String> details;

    /**
     * Creates a new page object representing the analysis summary on the build page of a job.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     * @param id
     *         the type of the result page (e.g. simian, checkstyle, cpd, etc.)
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public Summary(final Build parent, final String id) {
        super(parent, parent.url(id));

        summarySpan = getElement(By.id(id));
        title = REMOVE_DETAILS.matcher(summarySpan.getText()).replaceAll("");
        details = summarySpan.findElements(by.xpath("ul/li")).stream()
                .map(WebElement::getText)
                .map(StringUtils::normalizeSpace)
                .collect(Collectors.toList());
    }

    /**
     * Return the title of the summary as text.
     *
     * @return the title text
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the texts of the detail elements of the summary.
     *
     * @return the details
     */
    public List<String> getDetails() {
        return details;
    }

    /**
     * Opens a link given by the specified text.
     *
     * @param text
     *         the text of the link
     *
     * @return the URL of the page that has been opened by the link
     */
    public String openLinkByText(final String text) {
        summarySpan.findElement(By.linkText(text)).click();

        return driver.getCurrentUrl();
    }
}

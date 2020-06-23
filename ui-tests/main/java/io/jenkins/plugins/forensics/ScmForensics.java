package io.jenkins.plugins.forensics;

import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.inject.Injector;


import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the details page of the git-forensics tool results.
 *
 * @author Mitja Oldenbourg
 */
public class ScmForensics extends PageObject {

    private final String id;

    /**
     * Creates an instance of the page displaying the details of the issues for a specific tool.
     *
     * @param parent
     *         a finished build configured with a static analysis tool
     * @param id
     *         the type of the result page (e.g. simian, checkstyle, cpd, etc.)
     */
    public ScmForensics(final Build parent, final String id) {
        super(parent, parent.url(id));

        this.id = id;
    }

    /**
     * Creates an instance of the page displaying the details of the issues. This constructor is used for injecting a
     * filtered instance of the page (e.g. by clicking on links which open a filtered instance of a AnalysisResult.
     *
     * @param injector
     *         the injector of the page
     * @param url
     *         the url of the page
     * @param id
     *         the id of  the result page (e.g simian or cpd)
     */
    @SuppressWarnings("unused") // Required to dynamically create page object using reflection
    public ScmForensics(final Injector injector, final URL url, final String id) {
        super(injector, url);

        this.id = id;
    }

    /**
     * Returns the total number of issues. This method requires that one of the tabs is shown that shows the total
     * number of issues in the footer. I.e. the.
     *
     * @return the total number of issues
     */
    public int getTotal() {
        String total = find(By.id("forensics_info")).getText();

        return Integer.parseInt(StringUtils.substringAfter(total, "Total "));
    }


    /**
     * Opens a link on the page leading to another page.
     *
     * @param element
     *         the WebElement representing the link to be clicked
     * @param type
     *         the class of the PageObject which represents the page to which the link leads to
     * @param <T>
     *         actual type of the page object
     *
     * @return the instance of the PageObject to which the link leads to
     */
    // FIXME: IssuesTable should not depend on AnalysisResult
    public <T extends PageObject> T openLinkOnSite(final WebElement element, final Class<T> type) {
        String link = element.getAttribute("href");
        T retVal = newInstance(type, injector, url(link));
        element.click();
        return retVal;
    }

    /**
     * Opens a link to a filtered version of this AnalysisResult by clicking on a link.
     *
     * @param element
     *         the WebElement representing the link to be clicked
     *
     * @return the instance of the filtered AnalysisResult
     */
    // FIXME: IssuesTable should not depend on AnalysisResult
    public ScmForensics openFilterLinkOnSite(final WebElement element) {
        String link = element.getAttribute("href");
        ScmForensics retVal = newInstance(ScmForensics.class, injector, url(link), id);
        element.click();
        return retVal;
    }
}


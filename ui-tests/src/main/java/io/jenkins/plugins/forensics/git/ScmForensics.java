package io.jenkins.plugins.forensics.git;

import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;

import com.google.inject.Injector;

import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * {@link PageObject} representing the details page of the forensics miner results.
 *
 * @author Mitja Oldenbourg
 */
public class ScmForensics extends PageObject {
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
    }

    /**
     * Returns the total number of entries in the repository.
     *
     * @return the total number of entries
     */
    public int getTotal() {
        String total = find(By.id("forensics_info")).getText();

        return Integer.parseInt(StringUtils.substringAfter(total, "of ").split(" ")[0]);
    }
}


package io.jenkins.plugins.forensics;

import org.jenkinsci.test.acceptance.plugins.warnings_ng.ScrollerUtil;
import org.jenkinsci.test.acceptance.po.AbstractStep;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.PostBuildStep;

/**
 * Page object for the IssuesRecorder of the warnings plugin (white mountains release).
 *
 * @author Ullrich Hafner
 */
@Describable("Mine SCM repository")
public class ForensicsPublisher extends AbstractStep implements PostBuildStep {
    /**
     * Creates a new page object.
     *
     * @param parent
     *         parent page object
     * @param path
     *         path on the parent page
     */
    public ForensicsPublisher(final Job parent, final String path) {
        super(parent, path);

        ScrollerUtil.hideScrollerTabBar(driver);
    }
}

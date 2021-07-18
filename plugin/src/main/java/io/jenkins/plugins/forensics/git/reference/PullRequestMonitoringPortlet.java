package io.jenkins.plugins.forensics.git.reference;

import edu.hm.hafner.util.FilteredLog;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.forensics.miner.CommitStatistics;
import io.jenkins.plugins.forensics.miner.CommitStatisticsBuildAction;
import io.jenkins.plugins.forensics.reference.ReferenceFinder;
import io.jenkins.plugins.monitoring.MonitorPortlet;
import io.jenkins.plugins.monitoring.MonitorPortletFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * A portlet that can be used for the
 * <a href="https://github.com/jenkinsci/pull-request-monitoring-plugin">pull-request-monitoring</a> dashboard.
 *
 * It renders the commit statistics, such as added or removed files/lines as a portlet.
 *
 * @author Simon Symhoven
 */
public class PullRequestMonitoringPortlet extends MonitorPortlet {
    private final CommitStatistics commitStatistics;

    /**
     * Creates a new {@link PullRequestMonitoringPortlet}.
     *
     * @param commitStatistics
     *          the {@link CommitStatistics} of corresponding action {@link CommitStatisticsBuildAction}.
     */
    public PullRequestMonitoringPortlet(CommitStatistics commitStatistics) {
        this.commitStatistics = commitStatistics;
    }

    @Override
    public String getTitle() {
        return Messages.Portlet_Title();
    }

    @Override
    public String getId() {
        return "git-forensics";
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public int getPreferredWidth() {
        return 550;
    }

    @Override
    public int getPreferredHeight() {
        return 300;
    }

    @Override
    public Optional<String> getIconUrl() {
        return Optional.of("/plugin/git-forensics/icons/git-48x48.png");
    }

    @SuppressWarnings("unused") // (used by jelly view)
    public int getAuthorCount() {
        return this.commitStatistics.getAuthorCount();
    }

    @SuppressWarnings("unused") // (used by jelly view)
    public int getCommitCount() {
        return this.commitStatistics.getCommitCount();
    }

    @SuppressWarnings("unused") // (used by jelly view)
    public int getFilesCount() {
        return this.commitStatistics.getFilesCount();
    }

    @SuppressWarnings("unused") // (used by jelly vie w)
    public int getLinesOfCode() {
        return this.commitStatistics.getLinesOfCode();
    }

    @SuppressWarnings("unused") // (used by jelly view)
    public int getAddedLines() {
        return this.commitStatistics.getAddedLines();
    }

    @SuppressWarnings("unused") // (used by jelly view)
    public int getDeletedLines() {
        return this.commitStatistics.getDeletedLines();
    }

    /**
     * The factory for the {@link PullRequestMonitoringPortlet}.
     */
    @Extension(optional = true)
    public static class PullRequestMonitoringPortletFactory extends MonitorPortletFactory {

        @Override
        public Collection<MonitorPortlet> getPortlets(Run<?, ?> build) {
            Optional<Run<?, ?>> possibleReferenceBuild = new ReferenceFinder()
                    .findReference(build, new FilteredLog(""));

            if (possibleReferenceBuild.isPresent()) {
                CommitStatisticsBuildAction action = build.getAction(CommitStatisticsBuildAction.class);

                if (action == null) {
                    return Collections.emptyList();
                }

                return Collections.singleton(new PullRequestMonitoringPortlet(action.getCommitStatistics()));
            }

            return Collections.emptyList();
        }

        @Override
        public String getDisplayName() {
            return Messages.Portlet_Factory_Title();
        }
    }
}

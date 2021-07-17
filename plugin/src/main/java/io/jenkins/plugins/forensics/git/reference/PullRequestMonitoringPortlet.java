package io.jenkins.plugins.forensics.git.reference;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.monitoring.MonitorPortlet;
import io.jenkins.plugins.monitoring.MonitorPortletFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class PullRequestMonitoringPortlet extends MonitorPortlet {
    @Override
    public String getTitle() {
        return Messages.Portlet_Title();
    }

    @Override
    public String getId() {
        return "git-statistics";
    }

    @Override
    public int getPreferredWidth() {
        return 300;
    }

    @Override
    public int getPreferredHeight() {
        return 150;
    }

    @Override
    public Optional<String> getIconUrl() {
        return Optional.of("/plugin/git-forensics/icons/git-24x24.png");
    }

    @Override
    public Optional<String> getDetailViewUrl() {
        return Optional.empty();
    }

    @Extension(optional = true)
    public static class PullRequestMonitoringPortletFactory extends MonitorPortletFactory {

        @Override
        public Collection<MonitorPortlet> getPortlets(Run<?, ?> build) {
            return Collections.emptyList();
        }

        @Override
        public String getDisplayName() {
            return Messages.Portlet_Factory_Title();
        }
    }
}

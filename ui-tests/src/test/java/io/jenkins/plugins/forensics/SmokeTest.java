package io.jenkins.plugins.forensics;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.WorkflowJob;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.*;

public class SmokeTest extends AbstractJUnitTest {
    private static final String REPOSITORY_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";

    private static final int REFERENCE_BUILD_FOR_DELTA_REPORTS_ROW = 4;
    private static final int COMMIT_DIFF_STATISTICS_ROW = 5;

    private static final String CROSSHAIR_IMAGE_SRC = "crosshair.svg";
    private static final String DIFF_STAT_IMAGE_SRC = "diff-stat.svg";

    @Test
    public void shouldDisplayReferenceBuildForDeltaReports(){
        String firstCommitHash = "'28af63def44286729e3b19b03464d100fd1d0587'";
        String secondCommitHash = "'ad4af63e759e79677fe5774099cea073355e56b3'";

        // build first job, which is later used as reference
        WorkflowJob firstJob = createJob("  mineRepository() \n", firstCommitHash);
        Build firstBuild = buildSuccessfully(firstJob);

        // build second job
        String option = "discoverGitReferenceBuild(referenceJob: '" + firstJob.name + "') \n" + "gitDiffStat()"; // also works with discoverReferenceBuild
        WorkflowJob secondJob = createJob(option, secondCommitHash );
        Build secondBuild = buildSuccessfully(secondJob);


        // check text values of console
        assertThat(secondBuild.getConsole()).contains(
                "[Git DiffStats] Found 117 commits",
                "[Git DiffStats] -> 112 commits with differences analyzed",
                "[Git DiffStats] -> 661 MODIFY commit diff items",
                "[Git DiffStats] -> 94 RENAME commit diff items",
                "[Git DiffStats] -> 221 DELETE commit diff items",
                "[Git DiffStats] -> 26708 lines added",
                "[Git DiffStats] -> 24899 lines deleted");

        secondBuild.open();


        // TODO: create page objects
        // check text values of REFERENCE_BUILD_FOR_DELTA_REPORTS
        assertThat(getSummaryText(secondBuild, REFERENCE_BUILD_FOR_DELTA_REPORTS_ROW)).contains(
                "Reference build for delta reports",
                firstBuild.getName());

        // check text values of COMMIT_DIFF_STATISTICS
        assertThat(getSummaryText(secondBuild, COMMIT_DIFF_STATISTICS_ROW)).contains(
                "Commit Diff Statistics: git " + REPOSITORY_URL,
                "Commits: 112 - compared to target branch build " + firstBuild.getName(),
                "Changed files: 108",
                "Changed lines: 26708 added, 24899 deleted");


        // check if crosshair and diff-stat images are shown
        assertThat(isImageDisplayed(CROSSHAIR_IMAGE_SRC)).isTrue();
        assertThat(isImageDisplayed(DIFF_STAT_IMAGE_SRC)).isTrue();

        String linkToFirstBuild = firstBuild.getName().split(" ")[0] + '/' + firstBuild.getName().split(" ")[1].substring(1) + '/';

        // check if link to first build exists and if it is a valid link (same link for ReferenceBuildForDeltaReports and CommitDiffStatistics)
        assertThat(isLinkWorking(linkToFirstBuild)).isTrue();


        //check trend charts
        //System.out.println("-------------------------------------");
        //System.out.println("-------------------------------------");
    }


    private boolean isLinkWorking (String name){
        List<WebElement> links = driver.findElements(By.className("model-link"));
        for (WebElement link : links){
            String url = link.getAttribute("href");
            if(url.contains(name)){
                try {
                    HttpURLConnection huc = (HttpURLConnection) (new URL(link.getAttribute("href")).openConnection());
                    huc.setRequestMethod("HEAD");
                    huc.connect();

                    int respCode = huc.getResponseCode();
                    if (respCode >= 400) {
                        // System.out.println(link.getAttribute("href") + " is a broken link");
                        return false;
                    }
                    else {
                        // System.out.println(link.getAttribute("href") + " is a valid link");
                        return true;
                    }
                }catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    private boolean isImageDisplayed (String name) {
        List<WebElement> allImages = driver.findElements(By.tagName("img"));

        for (WebElement image : allImages){
            if(image.getAttribute("src").contains(name)){
                return image.isDisplayed();
            }
        }
        // image does not exist
        return false;
    }


    private String getSummaryText(final Build referenceBuild, final int row) {
        return referenceBuild.getElement(
                By.xpath("/html/body/div[4]/div[2]/table/tbody/tr[" + row + "]/td[2]")).getText();
    }


    private WorkflowJob createJob(String option, String commitHash) {
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.sandbox.check();
        job.script.set("node {\n"
                + "  checkout([$class: 'GitSCM', branches: [[name: "+ commitHash +" ]],\n"
                + "     userRemoteConfigs: [[url: '" + REPOSITORY_URL + "']]])\n"
                + option
                + "} \n");
        job.save();
        return job;
    }


    protected Build buildSuccessfully(final Job job) {
        return job.startBuild().waitUntilFinished().shouldSucceed();
    }
}

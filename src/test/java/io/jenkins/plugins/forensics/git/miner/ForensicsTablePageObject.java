package io.jenkins.plugins.forensics.git.miner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableBody;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.jenkins.plugins.forensics.git.miner.ForensicsRow.ForensicsColumn;

import static org.assertj.core.api.Assertions.*;

/**
 * Page Object for a table that shows the issues of a build.
 *
 * @author Ullrich Hafner
 */
public class ForensicsTablePageObject extends PageObject {
    private final List<ForensicsRow> rows = new ArrayList<>();
    private final List<ForensicsColumn> columns;

    /**
     * Creates a new instance of {@link IssuesTable}.
     *
     * @param page
     *         the whole details HTML page
     */
    @SuppressFBWarnings("BC")
    public ForensicsTablePageObject(final HtmlPage page) {
        super(page);

        DomElement issues = page.getElementById("forensics");
        assertThat(issues).isInstanceOf(HtmlTable.class);

        HtmlTable table = (HtmlTable) issues;
        List<HtmlTableRow> tableHeaderRows = table.getHeader().getRows();
        assertThat(tableHeaderRows).hasSize(1);

        HtmlTableRow header = tableHeaderRows.get(0);
        columns = getHeaders(header.getCells());

        List<HtmlTableBody> bodies = table.getBodies();
        assertThat(bodies).hasSize(1);

        int number = 0;
        while ("Loading - please wait ...".equals(table.getBodies().get(0).getRows().get(0).getFirstChild().getTextContent())) {
            System.out.println("Waiting for Ajax call to populate table ...");
            getPage().getEnclosingWindow().getJobManager().waitForJobs(1000);

            if (number++ > 10) {
                throw new AssertionError("Ajax not finished!");
            }
        }

        HtmlTableBody mainBody = bodies.get(0);
        List<HtmlTableRow> contentRows = mainBody.getRows();

        for (HtmlTableRow row : contentRows) {
            List<HtmlTableCell> rowCells = row.getCells();
            rows.add(new ForensicsRow(rowCells, columns));
        }
    }

    public List<ForensicsRow> getRows() {
        return rows;
    }

    public List<ForensicsColumn> getColumns() {
        return columns;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void waitForAjaxCall(final HtmlTableBody body) {
        while ("Loading - please wait ...".equals(
                body.getRows().get(0).getCells().get(0).getFirstChild().getTextContent())) {
            System.out.println("Waiting for Ajax call to populate table ...");
            body.getPage().getEnclosingWindow().getJobManager().waitForJobs(1000);
        }
    }

    private List<ForensicsColumn> getHeaders(final List<HtmlTableCell> cells) {
        return cells.stream()
                .map(HtmlTableCell::getTextContent)
                .map(StringUtils::upperCase)
                .map(ForensicsColumn::fromColumnHeader)
                .collect(Collectors.toList());
    }

    /**
     * Returns the row with the specified index.
     *
     * @param index
     *         index of the row
     *
     * @return the row
     */
    public ForensicsRow getRow(final int index) {
        return rows.get(index);
    }
}

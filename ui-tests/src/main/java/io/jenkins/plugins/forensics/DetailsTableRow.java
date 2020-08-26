package io.jenkins.plugins.forensics;

import java.util.Arrays;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static io.jenkins.plugins.forensics.DetailsTable.*;

/**
 * Describes one row in the DetailsTable on the ScmForensics Page.
 *
 */
public class DetailsTableRow {
    private static final String FILE_SEPARATOR = "/";

    private final WebElement row;
    private final DetailsTable detailsTable;

    /**
     * Constructor.
     *
     * @param rowElement
     *         row element as WebElement.
     *
     * @param detailsTable
     *         reference to the detailsTable page object which is showing the rows.
     */
    public DetailsTableRow(final WebElement rowElement, final DetailsTable detailsTable) {
        this.row = rowElement;
        this.detailsTable = detailsTable;
    }

    /**
     * returns the name of the file which is represented in this row.
     *
     * @return name of the File
     */
    public String getFileName() {
        List<String> cellContent = Arrays.asList(getCellContent(FILE_NAME).split(FILE_SEPARATOR));
        return cellContent.get(cellContent.size() - 1);
    }

    /**
     * returns the number of Authors which is represented in this row.
     *
     * @return number of authors.
     */
    public int getNumberOfAuthors() {
        return Integer.parseInt(getCellContent(AUTHORS));
    }

    /**
     * returns the number of Commits which is represented in this row.
     *
     * @return number of commits.
     */
    public int getNumberOfCommits() {
        return Integer.parseInt(getCellContent(COMMITS));
    }

    /**
     * returns the last commit date string which is represented in this row.
     *
     * @return date of the last commit as String.
     */
    public String getLastCommitDate() {
        return getCellContent(LAST_COMMIT);
    }

    /**
     * returns the date added string which is represented in this row.
     *
     * @return date this file was added as String.
     */
    public String getDateAdded() {
        return getCellContent(ADDED);
    }

    private WebElement getCell(final String header) {
        List<WebElement> cells = row.findElements(By.tagName("td"));
        return cells.get(this.detailsTable.getHeaders().indexOf(header));
    }


    private String getCellContent(final String header) {
        if (!detailsTable.getHeaders().contains(header)) {
            return "-";
        }
        return getCell(header).getText();
    }
}

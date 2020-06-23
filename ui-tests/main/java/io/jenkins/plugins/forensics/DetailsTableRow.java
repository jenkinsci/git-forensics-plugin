package io.jenkins.plugins.forensics;

import java.util.Arrays;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static io.jenkins.plugins.forensics.DetailsTable.*;

public class DetailsTableRow {
    private static final String FILE_SEPARATOR = "/";

    private final WebElement row;
    private final DetailsTable detailsTable;

    public DetailsTableRow(final WebElement rowElement, final DetailsTable detailsTable) {
        this.row = rowElement;
        this.detailsTable = detailsTable;
    }

    public String getFileName() {
        List<String> cellContent = Arrays.asList(getCellContent(FILE).split(FILE_SEPARATOR));
        return cellContent.get(cellContent.size() - 1);
    }

    public int getNumberOfAuthors() {
        return Integer.parseInt(getCellContent(AUTHORS));
    }

    public int getNumberOfCommits() {
        return Integer.parseInt(getCellContent(COMMITS));
    }

    public String getLastCommitDate() {
        return getCellContent(LAST_COMMIT);
    }

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

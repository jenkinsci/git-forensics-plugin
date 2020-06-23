package io.jenkins.plugins.forensics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

public class DetailsTable {
    public static final String FILE = "File";
    public static final String AUTHORS = "#Authors";
    public static final String COMMITS = "#Commits";
    public static final String LAST_COMMIT = "Last Commit";
    public static final String ADDED = "Added";

    private final ScmForensics scmForensics;
    private final List<DetailsTableRow> tableRows = new ArrayList<>();
    private final List<String> headers;
    private final WebElement detailsTable;

    public DetailsTable(final ScmForensics scmForensics) {
        this.scmForensics = scmForensics;
        detailsTable = scmForensics.find(By.id("forensics_wrapper"));
        headers = detailsTable.findElements(By.xpath(".//thead/tr/th"))
                .stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
        updateTableRows();
    }

    public int getHeaderSize() {
        return headers.size();
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<DetailsTableRow> getTableRows() {
        return this.tableRows;
    }

    public int getTableRowSize() {
        return tableRows.size();
    }

    public String getForensicsInfo() {
        return detailsTable.findElement(By.id("forensics_info")).getText();
    }

    public void clickOnPagination(int page) {
        List<WebElement> pages = detailsTable.findElements(By.xpath(".//ul/li"));
        int pageNumber = pages.size() >= page ? page - 1 : pages.size() - 1;
        pages.get(pageNumber).click();
        updateTableRows();
    }

    public void searchTable(final String searchString) {
        WebElement searchBar = detailsTable.findElement(By.tagName("input"));
        searchBar.sendKeys(searchString);
        updateTableRows();
    }

    public void clearSearch() {
        WebElement searchBar = detailsTable.findElement(By.tagName("input"));
        searchBar.clear();
        searchBar.sendKeys(Keys.ENTER);
        updateTableRows();
    }

    public void showTenEntries() {
        WebElement customSelect = detailsTable.findElement(By.tagName("select"));
        customSelect.click();
        customSelect.findElement(By.xpath(".//option[1]")).click();
    }

    public void showFiftyEntries() {
        WebElement customSelect = detailsTable.findElement(By.tagName("select"));
        customSelect.click();
        customSelect.findElement(By.xpath(".//option[3]")).click();
    }

    public void sortColumn(String headerName) {
        int option = getHeaders().indexOf(headerName);
        getHeaderAsWebElement(option + 1).click();
    }

    private void updateTableRows() {
        tableRows.clear();
        List<WebElement> tableRowsAsWebElements = detailsTable.findElements(By.xpath(".//tbody/tr"));
        tableRowsAsWebElements.forEach(element -> tableRows.add(new DetailsTableRow(element, this)));
    }

    private WebElement getHeaderAsWebElement(int option) {
        return detailsTable.findElement(By.xpath(String.format(".//thead/tr/th[%d]", option)));
    }
}

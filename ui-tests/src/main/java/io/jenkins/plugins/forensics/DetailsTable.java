package io.jenkins.plugins.forensics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;


/**
 * Page for the DetailsTable on SCMForensics Page.
 *
 * @author Mitja Oldenbourg
 */
public class DetailsTable {

    /**
     * name of file table head.
     */
    public static final String FILE = "File";

    /**
     * name of number of authors table head.
     */
    public static final String AUTHORS = "#Authors";

    /**
     * name of number of commits table head.
     */
    public static final String COMMITS = "#Commits";

    /**
     * name of last commit date table head.
     */
    public static final String LAST_COMMIT = "Last Commit";

    /**
     * name of date added Table Head.
     */
    public static final String ADDED = "Added";

    private final List<DetailsTableRow> tableRows = new ArrayList<>();
    private final List<String> headers;
    private final WebElement detailsTable;

    /**
     * constructor for DetailsTable.
     *
     * @param scmForensics
     *         reference to the parent scmForensics Page.
     */
    public DetailsTable(final ScmForensics scmForensics) {
        detailsTable = scmForensics.find(By.id("forensics_wrapper"));
        headers = detailsTable.findElements(By.xpath(".//thead/tr/th"))
                .stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
        updateTableRows();
    }

    /**
     * returns the Header Row Size.
     *
     * @return amount of columns as Integer.
     */
    public int getHeaderSize() {
        return headers.size();
    }

    /**
     * returns a List of Strings containing the Headers.
     *
     * @return table headers as list.
     */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * returns a List of DetailsTableRow objects that represent the rows of the Table.
     *
     * @return table rows as List.
     */
    public List<DetailsTableRow> getTableRows() {
        updateTableRows();
        return this.tableRows;
    }

    /**
     * returns the amount of row entries of the Table.
     *
     * @return amount of shown table rows.
     */
    public int getNumberOfTableEntries() {
        updateTableRows();
        return tableRows.size(); }

    /**
     * returns the forensics information on the bottom left of the Table showing the number of total entries and the
     * page we are on.
     *
     * @return forensics info string.
     */
    public String getForensicsInfo() {
        return detailsTable.findElement(By.id("forensics_info")).getText();
    }

    /**
     * click on pagination button. If param is greater than amount of Pages, click on last Page.
     *
     * @param page
     *         page number we want to click on.
     */
    public void clickOnPagination(final int page) {
        List<WebElement> pages = detailsTable.findElements(By.xpath(".//ul/li"));
        int pageNumber = pages.size() >= page ? page - 1 : pages.size() - 1;
        pages.get(pageNumber).click();
        updateTableRows();
    }

    /**
     * search for a table entry.
     *
     * @param searchString
     *         entry we want to search for.
     */
    public void searchTable(final String searchString) {
        WebElement searchBar = detailsTable.findElement(By.tagName("input"));
        searchBar.sendKeys(searchString);
        updateTableRows();
    }

    /**
     * clears the search field.
     */
    public void clearSearch() {
        WebElement searchBar = detailsTable.findElement(By.tagName("input"));
        searchBar.clear();
        searchBar.sendKeys(Keys.ENTER);
        updateTableRows();
    }

    /**
     * selects ten entries to be shown in the table.
     */
    public void showTenEntries() {
        WebElement customSelect = detailsTable.findElement(By.tagName("select"));
        customSelect.click();
        customSelect.findElement(By.xpath(".//option[1]")).click();
        updateTableRows();
    }

    /**
     * selects fifty entries to be shown in the table.
     */
    public void showFiftyEntries() {
        WebElement customSelect = detailsTable.findElement(By.tagName("select"));
        customSelect.click();
        customSelect.findElement(By.xpath(".//option[3]")).click();
        updateTableRows();
    }

    /**
     * sorts the table for a certain column.
     *
     * @param headerName
     *         name of the column we want to sort.
     */
    public void sortColumn(final String headerName) {
        int option = getHeaders().indexOf(headerName);
        getHeaderAsWebElement(option + 1).click();
        updateTableRows();
    }

    private void updateTableRows() {
        tableRows.clear();
        List<WebElement> tableRowsAsWebElements = detailsTable.findElements(By.xpath(".//tbody/tr"));
        tableRowsAsWebElements.forEach(element -> tableRows.add(new DetailsTableRow(element, this)));
    }

    private WebElement getHeaderAsWebElement(final int option) {
        return detailsTable.findElement(By.xpath(String.format(".//thead/tr/th[%d]", option)));
    }
}

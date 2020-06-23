package io.jenkins.plugins.forensics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class DetailsTable {
    private final ScmForensics scmForensics;
    private final List<DetailsTableRow> tableRows = new ArrayList<>();
    private final List<String> headers;
    private final WebElement tableElement;

    public DetailsTable(final ScmForensics scmForensics) {
        this.scmForensics = scmForensics;
        tableElement = scmForensics.find(By.id("forensics_wrapper"));
        headers = tableElement.findElements(By.xpath(".//thead/tr/th"))
                .stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
    }

    public int getHeaderSize() {
        return headers.size();
    }

    public List<String> getHeaders() {
        return headers;
    }
}

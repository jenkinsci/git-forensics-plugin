package io.jenkins.plugins.forensics.git.miner;

import java.util.List;

import com.gargoylesoftware.htmlunit.html.HtmlTableCell;

import edu.hm.hafner.util.NoSuchElementException;
import edu.hm.hafner.util.StringContainsUtils;

import io.jenkins.plugins.forensics.git.miner.ForensicsRow.ForensicsColumn;

/**
 * Simple Java bean that represents an issue row in the issues table.
 */
public class ForensicsRow extends TableRow<ForensicsColumn> {
    public enum ForensicsColumn {
        FILE("File"),
        AUTHORS("Authors"),
        COMMITS("Commits"),
        LAST_COMMIT("Last Commit"),
        ADDED("Added");

        private final String header;

        ForensicsColumn(final String header) {
            this.header = header;
        }

        static ForensicsColumn fromColumnHeader(final String header) {
            for (ForensicsColumn column : values()) {
                if (StringContainsUtils.containsAnyIgnoreCase(header, column.header)) {
                    return column;
                }
            }
            throw new NoSuchElementException("No such column with header '%s'", header);
        }
    }

    /**
     * Creates a new row  based on a list of HTML cells and columns.
     *
     * @param columnValues
     *         the values given as {@link HtmlTableCell}
     * @param columns
     *         the visible columns
     */
    ForensicsRow(final List<HtmlTableCell> columnValues, final List<ForensicsColumn> columns) {
        super(columns, columnValues);
    }
}

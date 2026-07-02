package io.jenkins.plugins.forensics.git.miner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link IndentationLevelCalculator}.
 *
 * @author Akash Manna
 */
class IndentationLevelCalculatorTest {
    private static final double TOLERANCE = 1e-9;

    private final IndentationLevelCalculator calculator = new IndentationLevelCalculator();

    @Test
    void shouldReturnEmptyResultForEmptyContent() {
        assertThat(calculator.compute("")).satisfies(IndentationLevelCalculatorTest::assertIsEmpty);
        assertThat(calculator.compute((String) null)).satisfies(IndentationLevelCalculatorTest::assertIsEmpty);
        assertThat(calculator.compute(List.of())).satisfies(IndentationLevelCalculatorTest::assertIsEmpty);
    }

    @Test
    void shouldIgnoreBlankAndWhitespaceOnlyLines() {
        var level = calculator.compute(List.of("", "   ", "\t", "\t  \t "));

        assertIsEmpty(level);
    }

    @Test
    void shouldCountSingleLineWithoutIndentationAsLevelZero() {
        var level = calculator.compute(List.of("no indentation at all"));

        assertThat(level.getNumberOfLines()).isEqualTo(1);
        assertThat(level.getTotal()).isZero();
        assertThat(level.getMaximum()).isZero();
        assertThat(level.getMean()).isEqualTo(0.0, within(TOLERANCE));
        assertThat(level.getStandardDeviation()).isEqualTo(0.0, within(TOLERANCE));
        assertThat(level.isEmpty()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] line=\"{0}\" -> level={1}")
    @CsvSource({
            "'foo()',                       0",
            "'\tfoo()',                     1",
            "'\t\tfoo()',                   2",
            "'\t\t\tfoo()',                 3",
            "'    foo()',                   1",   // exactly 4 spaces
            "'        foo()',               2",   // exactly 8 spaces
            "'   foo()',                    0",   // 3 spaces: not enough for a level
            "'     foo()',                  1",   // 5 spaces: 4 counted, 1 leftover ignored
            "'\t    foo()',                 2",   // 1 tab + 4 spaces
            "'    \tfoo()',                 2",   // 4 spaces + 1 tab
            "'  \tfoo()',                   1",   // 2 spaces (not enough) + 1 tab
            "'\t\t    foo()',               3",   // 2 tabs + 4 spaces
    })
    void shouldCountIndentationLevelOfSingleLine(final String line, final int expectedLevel) {
        assertThat(calculator.countIndentationLevel(line)).isEqualTo(expectedLevel);
    }

    @Test
    void shouldTreatRunsOfSpacesIndependently() {
        // 9 spaces: two full groups of four (=> level 2), one leftover space is ignored
        assertThat(calculator.countIndentationLevel("         foo")).isEqualTo(2);
    }

    @Test
    void shouldStopCountingAtFirstNonWhitespaceCharacter() {
        // indentation in the middle of the line (after the first token) must not be counted
        assertThat(calculator.countIndentationLevel("if (x) {    nested(); }")).isZero();
    }

    @Test
    void shouldAggregateMultipleLinesCorrectly() {
        var level = calculator.compute(List.of(
                "level0();",            // 0
                "\tlevel1();",          // 1
                "\t\tlevel2();",        // 2
                "\t\t\tlevel3();",      // 3
                ""                      // blank line - ignored
        ));

        assertThat(level.getNumberOfLines()).isEqualTo(4);
        assertThat(level.getTotal()).isEqualTo(6); // 0 + 1 + 2 + 3
        assertThat(level.getMaximum()).isEqualTo(3);
        assertThat(level.getMean()).isEqualTo(1.5, within(TOLERANCE));

        // population standard deviation of [0, 1, 2, 3] with mean 1.5
        double expectedVariance = (Math.pow(1.5, 2) + Math.pow(0.5, 2) + Math.pow(0.5, 2) + Math.pow(1.5, 2)) / 4;
        assertThat(level.getStandardDeviation()).isEqualTo(Math.sqrt(expectedVariance), within(TOLERANCE));
    }

    @Test
    void shouldComputeStandardDeviationOfZeroForUniformIndentation() {
        var level = calculator.compute(List.of("\tfoo();", "\tbar();", "\tbaz();"));

        assertThat(level.getNumberOfLines()).isEqualTo(3);
        assertThat(level.getTotal()).isEqualTo(3);
        assertThat(level.getMean()).isEqualTo(1.0, within(TOLERANCE));
        assertThat(level.getStandardDeviation()).isEqualTo(0.0, within(TOLERANCE));
        assertThat(level.getMaximum()).isEqualTo(1);
    }

    @Test
    void shouldSplitContentOnDifferentLineSeparators() {
        var unix = calculator.compute("\tfoo();\n\t\tbar();\n");
        var windows = calculator.compute("\tfoo();\r\n\t\tbar();\r\n");
        var mac = calculator.compute("\tfoo();\r\t\tbar();\r");

        for (var level : List.of(unix, windows, mac)) {
            assertThat(level.getNumberOfLines()).isEqualTo(2);
            assertThat(level.getTotal()).isEqualTo(3);
            assertThat(level.getMaximum()).isEqualTo(2);
        }
    }

    @Test
    void shouldHandleRealisticJavaSnippet() {
        var content = String.join("\n", List.of(
                "public class Example {",                   // 0
                "    public void run() {",                  // 1 (4 spaces)
                "        if (isValid()) {",                 // 2 (8 spaces)
                "            doSomething();",               // 3 (12 spaces)
                "        }",                                // 2
                "    }",                                    // 1
                "}"                                         // 0
        ));

        var level = calculator.compute(content);

        assertThat(level.getNumberOfLines()).isEqualTo(7);
        assertThat(level.getTotal()).isEqualTo(0 + 1 + 2 + 3 + 2 + 1 + 0);
        assertThat(level.getMaximum()).isEqualTo(3);
        assertThat(level.getMean()).isEqualTo(9.0 / 7.0, within(TOLERANCE));
    }

    private static void assertIsEmpty(final IndentationLevel level) {
        assertThat(level.isEmpty()).isTrue();
        assertThat(level.getNumberOfLines()).isZero();
        assertThat(level.getTotal()).isZero();
        assertThat(level.getMaximum()).isZero();
        assertThat(level.getMean()).isEqualTo(0.0, within(TOLERANCE));
        assertThat(level.getStandardDeviation()).isEqualTo(0.0, within(TOLERANCE));
    }
}
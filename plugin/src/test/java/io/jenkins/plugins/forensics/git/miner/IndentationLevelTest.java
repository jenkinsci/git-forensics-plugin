package io.jenkins.plugins.forensics.git.miner;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link IndentationLevel}.
 *
 * @author Akash Manna
 */
class IndentationLevelTest {
    private static final double TOLERANCE = 1e-9;

    @Test
    void shouldCreateEmptyResultForEmptyInput() {
        var level = new IndentationLevel(List.of());

        assertThat(level.isEmpty()).isTrue();
        assertThat(level.getNumberOfLines()).isZero();
        assertThat(level.getTotal()).isZero();
        assertThat(level.getMaximum()).isZero();
        assertThat(level.getMean()).isEqualTo(0.0, within(TOLERANCE));
        assertThat(level.getStandardDeviation()).isEqualTo(0.0, within(TOLERANCE));
    }

    @Test
    void shouldAggregateSingleValue() {
        var level = new IndentationLevel(List.of(4));

        assertThat(level.isEmpty()).isFalse();
        assertThat(level.getNumberOfLines()).isEqualTo(1);
        assertThat(level.getTotal()).isEqualTo(4);
        assertThat(level.getMaximum()).isEqualTo(4);
        assertThat(level.getMean()).isEqualTo(4.0, within(TOLERANCE));
        assertThat(level.getStandardDeviation()).isEqualTo(0.0, within(TOLERANCE));
    }

    @Test
    void shouldAggregateMultipleValues() {
        var level = new IndentationLevel(List.of(0, 2, 4, 6));

        assertThat(level.getNumberOfLines()).isEqualTo(4);
        assertThat(level.getTotal()).isEqualTo(12);
        assertThat(level.getMaximum()).isEqualTo(6);
        assertThat(level.getMean()).isEqualTo(3.0, within(TOLERANCE));

        // population standard deviation of [0, 2, 4, 6] with mean 3.0
        double expectedVariance = (9.0 + 1.0 + 1.0 + 9.0) / 4.0;
        assertThat(level.getStandardDeviation()).isEqualTo(Math.sqrt(expectedVariance), within(TOLERANCE));
    }

    @Test
    void shouldRejectNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> new IndentationLevel(null));
    }

    @Test
    void shouldHaveSharedEmptyConstant() {
        assertThat(IndentationLevel.EMPTY.isEmpty()).isTrue();
        assertThat(IndentationLevel.EMPTY).isEqualTo(new IndentationLevel(List.of()));
    }

    @Test
    void shouldObeyEqualsAndHashCodeContract() {
        EqualsVerifier.simple().forClass(IndentationLevel.class).verify();
    }

    @Test
    void shouldProvideReadableToString() {
        var level = new IndentationLevel(List.of(1, 2, 3));

        assertThat(level.toString())
                .contains("numberOfLines=3")
                .contains("total=6")
                .contains("maximum=3");
    }
}
package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks which texts are an identifier: only digits, from 1 to 10, without leading zeros. */
class ItemIdTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        "1", "9", "10", "83", "9999", "100000",
        // The longest ones: ten digits.
        "1234567890", "9999999999"
    })
    @DisplayName("A text of 1 to 10 digits that does not start with zero is an identifier, and it is kept as text")
    void digitsWithoutLeadingZerosAreAnIdentifier(String text) {
        assertThat(ItemId.isValid(text)).isTrue();
        assertThat(new ItemId(text).value()).isEqualTo(text);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        // A leading zero, alone or not.
        "0", "01", "007", "0000000001",
        // Something that is not a digit.
        "abc", "1a", "a1", "-3", "+3", "1.0", "1e3", "1_000",
        // Empty, or with spaces: nothing is trimmed.
        "", " ", " 1", "1 ", "1\n",
        // Eleven digits.
        "12345678901",
        // What arrives when the parameter is sent twice: id=1&id=2.
        "1,2",
        // Digits of another alphabet.
        "١٢"
    })
    @DisplayName("Any other text is not an identifier, and it is rejected naming the parameter id")
    void anyOtherTextIsRejected(String text) {
        assertThat(ItemId.isValid(text)).isFalse();

        InvalidParameterException error = catchThrowableOfType(InvalidParameterException.class, () -> new ItemId(text));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("id");
        assertThat(error.requirement()).isEqualTo("must be a number of 1 to 10 digits, without leading zeros");
    }

    @Test
    @DisplayName("A missing text is not an identifier either")
    void missingTextIsRejected() {
        assertThat(ItemId.isValid(null)).isFalse();
        assertThat(catchThrowableOfType(InvalidParameterException.class, () -> new ItemId(null))).isNotNull();
    }

    @Test
    @DisplayName("The message of the error never repeats the value that was received")
    void errorDoesNotRepeatTheValue() {
        InvalidParameterException error = catchThrowableOfType(
                InvalidParameterException.class, () -> new ItemId("<script>"));

        assertThat(error.getMessage()).doesNotContain("script");
    }

    @Test
    @DisplayName("The pattern shown in the API documentation accepts and rejects the same texts")
    void documentedPatternIsTheSameRule() {
        assertThat("1234567890").matches(ItemId.PATTERN);
        assertThat("12345678901").doesNotMatch(ItemId.PATTERN);
        assertThat("01").doesNotMatch(ItemId.PATTERN);
    }

}

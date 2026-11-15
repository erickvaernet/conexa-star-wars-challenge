package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the page and the size of a request: their default values and what is rejected. */
class PageQueryTest {

    @Test
    @DisplayName("Without parameters, it is the first page of 10")
    void withoutParametersItIsTheFirstPageOfTen() {
        assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(1, 10));
    }

    @Test
    @DisplayName("Each parameter has its own default value")
    void eachParameterHasItsOwnDefaultValue() {
        assertThat(PageQuery.of("3", null)).isEqualTo(new PageQuery(3, 10));
        assertThat(PageQuery.of(null, "25")).isEqualTo(new PageQuery(1, 25));
    }

    @Test
    @DisplayName("The page and the size that were asked for are kept")
    void pageAndSizeAreKept() {
        assertThat(PageQuery.of("9", "10")).isEqualTo(new PageQuery(9, 10));
    }

    @Test
    @DisplayName("The ends of the range are accepted: page 1, and sizes 1 and 100")
    void endsOfTheRangeAreAccepted() {
        assertThat(PageQuery.of("1", "1")).isEqualTo(new PageQuery(1, 1));
        assertThat(PageQuery.of("1", "100")).isEqualTo(new PageQuery(1, 100));
    }

    @Test
    @DisplayName("There is no last page number: a page far after the last one is a valid request")
    void thereIsNoLastPageNumber() {
        assertThat(PageQuery.of("2147483647", null).page()).isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest(name = "page=\"{0}\"")
    @ValueSource(strings = {
        "0", "-1", "abc", "", " ", "1.5", "1e3", " 2", "2 ",
        // More than an integer can hold.
        "2147483648", "99999999999",
        // What arrives when the parameter is sent twice: page=1&page=2.
        "1,2"
    })
    @DisplayName("A page that is not an integer of 1 or more is rejected, naming the parameter")
    void invalidPageIsRejected(String page) {
        InvalidParameterException error =
                catchThrowableOfType(InvalidParameterException.class, () -> PageQuery.of(page, null));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("page");
        assertThat(error.requirement()).isEqualTo("must be an integer greater than or equal to 1");
    }

    @ParameterizedTest(name = "size=\"{0}\"")
    @ValueSource(strings = {
        "0", "-1", "101", "abc", "", " ", "10.0", " 10", "10 ", "99999999999", "10,20"
    })
    @DisplayName("A size that is not an integer from 1 to 100 is rejected, naming the parameter")
    void invalidSizeIsRejected(String size) {
        InvalidParameterException error =
                catchThrowableOfType(InvalidParameterException.class, () -> PageQuery.of(null, size));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("size");
        assertThat(error.requirement()).isEqualTo("must be an integer between 1 and 100");
    }

    // U+0661 is the Arabic-Indic digit one, U+FF11 the fullwidth digit one and U+0967 the
    // Devanagari digit one. Java reads all of them as a 1.
    @ParameterizedTest(name = "page=\"{0}\"")
    @ValueSource(strings = {"+1", "+10", "\u0661", "\u0661\u0660", "\uFF11", "\u0967", "1\u0660"})
    @DisplayName("A page written with a sign or with digits of another alphabet is rejected: only the digits 0 to 9 count")
    void pageThatIsNotWrittenWithPlainDigitsIsRejected(String page) {
        InvalidParameterException error =
                catchThrowableOfType(InvalidParameterException.class, () -> PageQuery.of(page, null));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("page");
        assertThat(error.requirement()).isEqualTo("must be an integer greater than or equal to 1");
    }

    @ParameterizedTest(name = "size=\"{0}\"")
    @ValueSource(strings = {"+5", "+100", "\u0665", "\u0661\u0660", "\uFF15", "\u096B", "1\u0660"})
    @DisplayName("A size written with a sign or with digits of another alphabet is rejected: only the digits 0 to 9 count")
    void sizeThatIsNotWrittenWithPlainDigitsIsRejected(String size) {
        InvalidParameterException error =
                catchThrowableOfType(InvalidParameterException.class, () -> PageQuery.of(null, size));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("size");
        assertThat(error.requirement()).isEqualTo("must be an integer between 1 and 100");
    }

    @Test
    @DisplayName("Zeros on the left do not change the number: 01 is 1")
    void zerosOnTheLeftAreAccepted() {
        assertThat(PageQuery.of("01", "010")).isEqualTo(new PageQuery(1, 10));
        assertThat(PageQuery.of("007", "0100")).isEqualTo(new PageQuery(7, 100));
    }

    @Test
    @DisplayName("A query cannot be built with invalid values either")
    void queryCannotBeBuiltWithInvalidValues() {
        assertThat(catchThrowableOfType(InvalidParameterException.class, () -> new PageQuery(0, 10)))
                .isNotNull();
        assertThat(catchThrowableOfType(InvalidParameterException.class, () -> new PageQuery(1, 101)))
                .isNotNull();
    }

    @Test
    @DisplayName("The message of the error never repeats the value that was received")
    void errorDoesNotRepeatTheValue() {
        InvalidParameterException error = catchThrowableOfType(
                InvalidParameterException.class, () -> PageQuery.of("<script>", null));

        assertThat(error.getMessage()).doesNotContain("script");
    }

}

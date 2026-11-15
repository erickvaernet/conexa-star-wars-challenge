package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks which texts can be searched for, from 1 to 100 characters without counting the spaces
 * around them, the form in which they are kept, and when a name contains one.
 */
class NameFilterTest {

    private static final String RULE = "must have 1 to 100 characters, not counting leading and trailing spaces";

    @ParameterizedTest(name = "\"{0}\" is kept as \"{1}\"")
    @CsvSource(ignoreLeadingAndTrailingWhitespace = false, value = {
        "sky,sky",
        // Upper and lower case are never told apart, so the text is kept in lower case.
        "SKY,sky",
        "Sky,sky",
        "Luke Skywalker,luke skywalker",
        // The spaces around the text are ignored. The ones inside it are part of what is searched.
        "  sky ,sky",
        " obi-wan  kenobi ,obi-wan  kenobi",
        // Anything else is searched as it is.
        "r2-d2,r2-d2",
        "l.ke,l.ke",
        "É,é"
    })
    @DisplayName("A text is kept without the spaces around it and in lower case")
    void textIsKeptWithoutSpacesAroundItAndInLowerCase(String text, String kept) {
        assertThat(new NameFilter(text).value()).isEqualTo(kept);
    }

    @Test
    @DisplayName("Two texts that differ only in upper and lower case, or in the spaces around them, are the same filter")
    void textsThatDifferOnlyInCaseOrOuterSpacesAreTheSameFilter() {
        assertThat(new NameFilter("  SKY ")).isEqualTo(new NameFilter("sky")).hasSameHashCodeAs(new NameFilter("sky"));
        assertThat(new NameFilter("sky")).isNotEqualTo(new NameFilter("skyw"));
    }

    @Test
    @DisplayName("The limits are accepted: 1 character and 100 characters")
    void limitsAreAccepted() {
        assertThat(new NameFilter("a").value()).isEqualTo("a");
        assertThat(new NameFilter("a".repeat(100)).value()).hasSize(100);
    }

    @Test
    @DisplayName("The spaces around the text do not count towards the 100 characters")
    void outerSpacesDoNotCount() {
        String text = " ".repeat(50) + "a".repeat(100) + " ".repeat(50);

        assertThat(new NameFilter(text).value()).isEqualTo("a".repeat(100));
    }

    @Test
    @DisplayName("The length is counted in characters: 100 characters that take two units each in Java are accepted")
    void lengthIsCountedInCharacters() {
        // Each of these is one character and two units of a Java text.
        String hundred = "😀".repeat(100);

        assertThat(hundred).hasSize(200);
        assertThat(new NameFilter(hundred).value()).isEqualTo(hundred);
        assertThat(catchThrowableOfType(InvalidParameterException.class, () -> new NameFilter(hundred + "😀")))
                .isNotNull();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        // Empty, or nothing but spaces of any kind.
        "", " ", "   ", "\t", " \n ",
    })
    @DisplayName("A text that is empty once the spaces around it are removed is rejected naming the parameter name")
    void blankTextIsRejected(String text) {
        assertRejected(text);
    }

    @Test
    @DisplayName("A text of more than 100 characters is rejected, also between spaces and with spaces inside")
    void tooLongTextIsRejected() {
        assertRejected("a".repeat(101));
        assertRejected("  " + "a".repeat(101) + "  ");
        // The spaces inside the text do count: 50 + 1 + 50.
        assertRejected("a".repeat(50) + " " + "a".repeat(50));
        assertRejected("a".repeat(5000));
    }

    @Test
    @DisplayName("A missing text is rejected as well")
    void missingTextIsRejected() {
        assertRejected(null);
    }

    @Test
    @DisplayName("The message of the error never repeats the value that was received")
    void errorDoesNotRepeatTheValue() {
        InvalidParameterException error = catchThrowableOfType(
                InvalidParameterException.class, () -> new NameFilter("<script>".repeat(20)));

        assertThat(error.getMessage())
                .isEqualTo("Parameter 'name' " + RULE + ".")
                .doesNotContain("script");
    }

    @ParameterizedTest(name = "\"{1}\" contains \"{0}\"")
    @CsvSource({
        "luke, Luke Skywalker",
        "LUKE, Luke Skywalker",
        "sky, Luke Skywalker",
        "walker, Luke Skywalker",
        "e s, Luke Skywalker",
        "luke skywalker, Luke Skywalker",
        "hope, A New Hope",
        "r2, R2-D2",
        "é, Padmé Amidala",
        "É, Padmé Amidala"
    })
    @DisplayName("A name matches when it contains the text, anywhere and without telling upper from lower case")
    void nameThatContainsTheTextMatches(String text, String name) {
        assertThat(new NameFilter(text).matches(name)).isTrue();
    }

    @ParameterizedTest(name = "\"{1}\" does not contain \"{0}\"")
    @CsvSource({
        "vader, Luke Skywalker",
        "lukes, Luke Skywalker",
        "skywalker luke, Luke Skywalker",
        // The text is not a pattern: a point is a point.
        "l.ke, Luke Skywalker",
        "e, Padmé"
    })
    @DisplayName("A name does not match when it does not contain the text as it is")
    void nameThatDoesNotContainTheTextDoesNotMatch(String text, String name) {
        assertThat(new NameFilter(text).matches(name)).isFalse();
    }

    @Test
    @DisplayName("The comparison does not depend on the language of the machine")
    void comparisonDoesNotDependOnTheDefaultLocale() {
        Locale before = Locale.getDefault();
        // In Turkish, the lower case of "I" is not "i" but a dotless letter.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(new NameFilter("WICKET").value()).isEqualTo("wicket");
            assertThat(new NameFilter("wicket").matches("WICKET SYSTRI WARRICK")).isTrue();
            assertThat(new NameFilter("WICKET").matches("Wicket Systri Warrick")).isTrue();
        } finally {
            Locale.setDefault(before);
        }
    }

    private static void assertRejected(String text) {
        InvalidParameterException error = catchThrowableOfType(
                InvalidParameterException.class, () -> new NameFilter(text));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("name");
        assertThat(error.requirement()).isEqualTo(RULE);
    }

}

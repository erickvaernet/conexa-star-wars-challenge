package io.github.erickvaernet.starwars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks the rule that limits a text by the bytes it takes in UTF-8, which is what BCrypt reads. */
class MaxUtf8BytesTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("textsOfUpTo72Bytes")
    @DisplayName("A text that takes 72 bytes or less is valid")
    void textOfUpTo72BytesIsValid(String text) {
        assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);

        assertThat(VALIDATOR.validate(new Sample(text))).isEmpty();
    }

    /** The last four take exactly 72 bytes: 1, 2, 3 and 4 bytes for each character. */
    static Stream<String> textsOfUpTo72Bytes() {
        return Stream.of("", "abc", "p".repeat(72), "é".repeat(36), "€".repeat(24), "🚀".repeat(18));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("textsOfMoreThan72Bytes")
    @DisplayName("A text that takes more than 72 bytes is invalid, however few characters it has, and the error carries the text of the rule")
    void textOfMoreThan72BytesIsInvalid(String text) {
        assertThat(text.length()).isLessThanOrEqualTo(73);
        assertThat(text.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        Set<ConstraintViolation<Sample>> errors = VALIDATOR.validate(new Sample(text));

        assertThat(errors).hasSize(1);
        assertThat(errors.iterator().next().getMessage()).isEqualTo("the text of the rule");
        assertThat(errors.iterator().next().getPropertyPath().toString()).isEqualTo("text");
    }

    /** One character more than each of the above: 73, 74, 75 and 76 bytes. */
    static Stream<String> textsOfMoreThan72Bytes() {
        return Stream.of("p".repeat(73), "é".repeat(37), "€".repeat(25), "🚀".repeat(19));
    }

    @Test
    @DisplayName("A missing text is not judged by this rule: saying that it is mandatory is another rule")
    void missingTextIsValid() {
        assertThat(VALIDATOR.validate(new Sample(null))).isEmpty();
    }

    /** A body with the rule on one of its fields, the way a real one declares it. */
    record Sample(@MaxUtf8Bytes(value = 72, message = "the text of the rule") String text) {
    }

}

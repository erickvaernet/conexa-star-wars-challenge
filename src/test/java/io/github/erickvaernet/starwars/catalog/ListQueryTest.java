package io.github.erickvaernet.starwars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.erickvaernet.starwars.shared.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks which texts the parameter {@code expanded} accepts: exactly "true" or "false". */
class ListQueryTest {

    @Test
    @DisplayName("The text \"true\" asks for the complete data, and the text \"false\" for the summaries")
    void trueAndFalseAreAccepted() {
        assertThat(ListQuery.expandedFrom("true")).isTrue();
        assertThat(ListQuery.expandedFrom("false")).isFalse();
    }

    @Test
    @DisplayName("A parameter that was not sent takes its default value: the summaries")
    void missingParameterIsFalse() {
        assertThat(ListQuery.expandedFrom(null)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        // The examples of the specification. The last one is the parameter sent without a value.
        "1", "TRUE", "",
        // Other ways of writing a boolean, which Java or Spring would accept on their own.
        "True", "FALSE", "False", "0", "yes", "no", "on", "off", "t", "f",
        // With spaces: nothing is trimmed.
        " ", " true", "true ", "false\n",
        // What arrives when the parameter is sent twice: expanded=true&expanded=true.
        "true,true"
    })
    @DisplayName("Any other text is rejected naming the parameter expanded, with the text of the specification")
    void anyOtherTextIsRejected(String text) {
        InvalidParameterException error =
                catchThrowableOfType(InvalidParameterException.class, () -> ListQuery.expandedFrom(text));

        assertThat(error).isNotNull();
        assertThat(error.parameter()).isEqualTo("expanded");
        assertThat(error.requirement()).isEqualTo("must be true or false");
        // What the client reads: the name and the rule, and never the value that was sent.
        assertThat(error).hasMessage("Parameter 'expanded' must be true or false.");
    }

}

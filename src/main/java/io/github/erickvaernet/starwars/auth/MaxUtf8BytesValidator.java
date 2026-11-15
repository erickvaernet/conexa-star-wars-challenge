package io.github.erickvaernet.starwars.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * Checks {@link MaxUtf8Bytes}: counts the bytes of the text in UTF-8.
 *
 * <p>It is public because the validation library creates it, and outside Spring it cannot
 * create a class that is not.
 */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes rule) {
        this.max = rule.value();
    }

    @Override
    public boolean isValid(String text, ConstraintValidatorContext context) {
        return text == null || text.getBytes(StandardCharsets.UTF_8).length <= max;
    }

}

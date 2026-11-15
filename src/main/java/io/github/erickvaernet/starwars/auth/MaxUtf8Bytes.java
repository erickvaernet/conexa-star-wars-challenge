package io.github.erickvaernet.starwars.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A text that takes at most this many bytes when it is written in UTF-8.
 *
 * <p>The rules that come with the validation count characters, and BCrypt reads bytes: a
 * password of 40 letters with an accent has 40 characters and takes 80 bytes. A missing text is
 * valid: saying that a field is mandatory is another rule.
 *
 * <p>It is a rule of a field, so that the answer to an invalid body can name the field. The
 * text has no default value on purpose: a field declares the same text in all its rules.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Documented
@interface MaxUtf8Bytes {

    /** The most bytes that the text may take. */
    int value();

    /** What the answer says about the field when it breaks the rule. */
    String message();

    /** Required by the validation; not used. */
    Class<?>[] groups() default {};

    /** Required by the validation; not used. */
    Class<? extends Payload>[] payload() default {};

}

package io.github.erickvaernet.starwars.shared;

/**
 * A user cannot be created because its name is already taken. It is answered with a 409.
 *
 * <p>It carries nothing: the answer is a fixed text. It is here, and not next to the request that
 * creates users, because every error of the API is answered from this package, which uses no other.
 */
public class UsernameAlreadyExistsException extends RuntimeException {
}

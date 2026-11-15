package io.github.erickvaernet.starwars.shared;

/**
 * The element that was asked for by its address does not exist. It is answered with a 404.
 *
 * <p>It carries nothing: the answer is a fixed text, the same as for a route that does not exist.
 */
public class NotFoundException extends RuntimeException {
}

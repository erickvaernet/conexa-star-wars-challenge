/**
 * The only boundary with SWAPI: HTTP requests, translation of its responses and cache.
 *
 * <p>Works as an anti-corruption layer. The SWAPI format never leaves this package: responses
 * are translated here into the model defined by {@code catalog}.
 */
package io.github.erickvaernet.starwars.swapi;

package io.github.erickvaernet.starwars.auth;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Reads and stores users in the database. Spring Data writes the implementation.
 *
 * <p>It offers only what the login and the creation of users need: nothing here changes, lists
 * or deletes a user.
 */
interface UserRepository extends Repository<User, Long> {

    /** Finds a user by its exact name. */
    Optional<User> findByUsername(String username);

    /** Tells whether a user has exactly this name. */
    boolean existsByUsername(String username);

    /**
     * Stores a new user.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if the database refuses the
     *         row, which is what happens when another user has that name
     */
    User save(User user);

}

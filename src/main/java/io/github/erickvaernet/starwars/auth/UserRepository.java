package io.github.erickvaernet.starwars.auth;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Reads users from the database. Spring Data writes the implementation.
 *
 * <p>It offers only what the login needs: the MVP neither creates nor changes users.
 */
interface UserRepository extends Repository<User, Long> {

    /** Finds a user by its exact name. */
    Optional<User> findByUsername(String username);

}

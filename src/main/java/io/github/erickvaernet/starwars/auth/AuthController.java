package io.github.erickvaernet.starwars.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login request: {@code POST /api/v1/auth/login}.
 *
 * <p>It is one of the few public requests. An invalid body is answered with 400 and wrong
 * credentials with 401, both as Problem Details.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;

    AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** Exchanges a user name and a password for a token. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.username(), request.password());
    }

}

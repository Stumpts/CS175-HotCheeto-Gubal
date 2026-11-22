package hotcheeto.gubal.controller;

import hotcheeto.gubal.entity.Account;
import hotcheeto.gubal.repository.AccountRepo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepo accountRepo;

    public AccountController(AccountRepo accountRepo) {
        this.accountRepo = accountRepo;
    }

    public record AuthRequest(String username, String password, String email) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.email == null || request.email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username, email, and password are required."));
        }

        if (accountRepo.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username is already taken."));
        }

        if (accountRepo.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already in use."));
        }

        Account newAccount = Account.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(request.password())
                .role(Account.Role.BORROWER)
                .build();

        accountRepo.save(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created", "username", newAccount.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Optional<Account> accountOpt = accountRepo.findByUsername(request.username());
        if (accountOpt.isEmpty() || !accountOpt.get().getPasswordHash().equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password."));
        }
        return ResponseEntity.ok(Map.of("message", "Login successful", "username", accountOpt.get().getUsername()));
    }
}
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

    public record CreateEmployeeRequest(String employeeUsername, String username, String password, String email) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.email() == null || request.email().isBlank()) {
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
                .username(request.username().trim())
                .email(request.email().trim())
                .passwordHash(request.password())
                .role(Account.Role.BORROWER)
                .build();

        Account saved = accountRepo.save(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Account created",
                        "username", saved.getUsername(),
                        "role", saved.getRole().name(),
                        "accountId", saved.getId()
                ));
    }

    @PostMapping("/employees")
    public ResponseEntity<?> createEmployee(@RequestBody CreateEmployeeRequest request) {
        if (request.employeeUsername() == null || request.employeeUsername().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Employee authentication is required."));
        }

        Optional<Account> requesterOpt = accountRepo.findByUsername(request.employeeUsername().trim());
        if (requesterOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Employee account not found."));
        }

        if (requesterOpt.get().getRole() != Account.Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Employee privileges are required to create an employee account."));
        }

        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username, email, and password are required."));
        }

        if (accountRepo.existsByUsername(request.username().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username is already taken."));
        }

        if (accountRepo.existsByEmail(request.email().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already in use."));
        }

        Account employee = Account.builder()
                .username(request.username().trim())
                .email(request.email().trim())
                .passwordHash(request.password())
                .role(Account.Role.EMPLOYEE)
                .build();

        Account saved = accountRepo.save(employee);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Employee account created",
                        "username", saved.getUsername(),
                        "role", saved.getRole().name(),
                        "accountId", saved.getId()
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required."));
        }
        Optional<Account> accountOpt = accountRepo.findByUsername(request.username().trim());
        if (accountOpt.isEmpty() || !accountOpt.get().getPasswordHash().equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password."));
        }
        Account account = accountOpt.get();
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "username", account.getUsername(),
                "role", account.getRole().name(),
                "accountId", account.getId()
        ));
    }
}
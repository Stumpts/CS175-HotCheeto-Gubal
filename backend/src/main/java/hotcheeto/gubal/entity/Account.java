package hotcheeto.gubal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Accounts")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public enum Role {
        EMPLOYEE,
        BORROWER
    }
}

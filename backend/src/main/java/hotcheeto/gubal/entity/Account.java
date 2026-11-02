package hotcheeto.gubal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long account_id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    enum role {
        EMPLOYEE,
        BORROWER
    }
}

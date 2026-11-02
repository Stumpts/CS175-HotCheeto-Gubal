package hotcheeto.gubal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Checkout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checkout_id;

    @Column(nullable = false)
    private Book book_id;

    @Column(nullable = false, unique = true)
    private Account borrower;

    @Column(nullable = false)
    private Date checkout_date;

    @Column(nullable = false)
    private Date due_date;

    @Column
    private Date returned;
}

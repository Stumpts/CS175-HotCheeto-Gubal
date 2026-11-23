package hotcheeto.gubal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "book_copies")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class BookCopy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "book_id")
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability")
    @Builder.Default
    private Availability availability = Availability.AVAILABLE;

    public Availability getEffectiveAvailability() {
        return availability != null ? availability : Availability.AVAILABLE;
    }

    public enum Availability {
        AVAILABLE,
        CHECKED_OUT,
        MISSING,
        LOST_AND_PAID,
        REQUESTED,
        IN_HOUSE_USAGE
    }
}

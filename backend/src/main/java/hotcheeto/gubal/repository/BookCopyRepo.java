package hotcheeto.gubal.repository;

import hotcheeto.gubal.entity.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookCopyRepo extends JpaRepository<BookCopy, Long> {
    boolean existsByBarcode(String barcode);
    Optional<BookCopy> findByBarcode(String barcode);
}

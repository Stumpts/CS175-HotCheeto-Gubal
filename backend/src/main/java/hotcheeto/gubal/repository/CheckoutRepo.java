package hotcheeto.gubal.repository;

import hotcheeto.gubal.entity.BookCopy;
import hotcheeto.gubal.entity.Checkout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckoutRepo extends JpaRepository<Checkout, Long> {
    Optional<Checkout> findFirstByBookAndReturnedIsNull(BookCopy book);
    List<Checkout> findByReturnedIsNull();
    List<Checkout> findByBorrowerUsernameIgnoreCaseAndReturnedIsNull(String username);
}

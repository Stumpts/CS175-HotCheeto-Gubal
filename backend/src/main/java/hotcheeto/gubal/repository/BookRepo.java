package hotcheeto.gubal.repository;

import hotcheeto.gubal.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepo extends JpaRepository<Book, Long> {
    boolean existsByTitle(String title);
    Optional<Book> findByTitle(String title);
    boolean existsByIsbn(String isbn);
    Optional<Book> findByIsbn(String isbn);
    boolean existsByAuthor(String author);
    Optional<Book> findByAuthor(String author);

    List<Book> findByTitleContainingIgnoreCase(String title);
    List<Book> findByAuthorContainingIgnoreCase(String author);
    List<Book> findByIsbnContainingIgnoreCase(String isbn);
}

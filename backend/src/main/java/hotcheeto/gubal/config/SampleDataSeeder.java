package hotcheeto.gubal.config;

import hotcheeto.gubal.entity.Account;
import hotcheeto.gubal.entity.Book;
import hotcheeto.gubal.entity.BookCopy;
import hotcheeto.gubal.repository.AccountRepo;
import hotcheeto.gubal.repository.BookCopyRepo;
import hotcheeto.gubal.repository.BookRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SampleDataSeeder implements CommandLineRunner {

    private final AccountRepo accountRepo;
    private final BookRepo bookRepo;
    private final BookCopyRepo bookCopyRepo;

    public SampleDataSeeder(AccountRepo accountRepo, BookRepo bookRepo, BookCopyRepo bookCopyRepo) {
        this.accountRepo = accountRepo;
        this.bookRepo = bookRepo;
        this.bookCopyRepo = bookCopyRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAccounts();
        seedBooksAndCopies();
    }

    private void seedAccounts() {
        if (!accountRepo.existsByUsername("reader") && !accountRepo.existsByEmail("reader@gubal.lib")) {
            accountRepo.save(
                    Account.builder()
                            .username("reader")
                            .email("reader@gubal.lib")
                            .passwordHash("password123")
                            .role(Account.Role.BORROWER)
                            .build()
            );
        }

        if (!accountRepo.existsByUsername("librarian") && !accountRepo.existsByEmail("librarian@gubal.lib")) {
            accountRepo.save(
                    Account.builder()
                            .username("librarian")
                            .email("librarian@gubal.lib")
                            .passwordHash("admin123")
                            .role(Account.Role.EMPLOYEE)
                            .build()
            );
        }
    }

    private void seedBooksAndCopies() {
        if (bookRepo.count() > 0) {
            return;
        }

        seedBook("The Great Gatsby", "F. Scott Fitzgerald", "9780743273565",
                List.of("E0:04:01:00:1A:2B", "E0:04:01:00:1A:2C"));
        seedBook("To Kill a Mockingbird", "Harper Lee", "9780061120084",
                List.of("E0:04:01:00:3C:4D"));
        seedBook("The Hobbit", "J.R.R. Tolkien", "9780547928227",
                List.of("E0:04:01:00:5E:6F", "E0:04:01:00:5E:70"));
        seedBook("Pride and Prejudice", "Jane Austen", "9780141439518",
                List.of("E0:04:01:00:7A:8B"));
        seedBook("1984", "George Orwell", "9780451524935",
                List.of("E0:04:01:00:9C:0D"));
        seedBook("The Alchemist", "Paulo Coelho", "9780062315007",
                List.of("E0:04:01:00:1E:2F"));
    }

    private void seedBook(String title, String author, String isbn, List<String> barcodes) {
        Book savedBook = bookRepo.save(
                Book.builder()
                        .title(title)
                        .author(author)
                        .isbn(isbn)
                        .build()
        );

        for (String barcode : barcodes) {
            bookCopyRepo.save(
                    BookCopy.builder()
                            .barcode(barcode)
                            .book(savedBook)
                            .build()
            );
        }
    }
}

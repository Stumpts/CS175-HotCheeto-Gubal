package hotcheeto.gubal.service;

import hotcheeto.gubal.entity.Book;
import hotcheeto.gubal.entity.BookCopy;
import hotcheeto.gubal.entity.Checkout;
import hotcheeto.gubal.repository.BookRepo;
import hotcheeto.gubal.repository.CheckoutRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepo bookRepo;
    private final CheckoutRepo checkoutRepo;

    public BookService(BookRepo bookRepo, CheckoutRepo checkoutRepo) {
        this.bookRepo = bookRepo;
        this.checkoutRepo = checkoutRepo;
    }

    public record BookSummary(
            Long id,
            String title,
            String author,
            String isbn,
            int copyCount,
            int availableCount,
            List<String> barcodes
    ) {}

    public List<Book> searchBooks(String type, String query) {
        if (query == null || query.isBlank()) {
            return bookRepo.findAll();
        }
        String q = query.trim();
        return switch (type == null ? "title" : type.toLowerCase()) {
            case "author" -> bookRepo.findByAuthorContainingIgnoreCase(q);
            case "isbn" -> bookRepo.findByIsbnContainingIgnoreCase(q);
            default -> bookRepo.findByTitleContainingIgnoreCase(q);
        };
    }

    @Transactional(readOnly = true)
    public List<BookSummary> searchBookSummaries(String type, String query) {
        Set<Long> checkedOutCopyIds = new HashSet<>();
        for (Checkout checkout : checkoutRepo.findByReturnedIsNull()) {
            if (checkout.getBook() != null && checkout.getBook().getId() != null) {
                checkedOutCopyIds.add(checkout.getBook().getId());
            }
        }

        return searchBooks(type, query).stream()
                .map(book -> {
                    List<BookCopy> copies = book.getCopies() != null ? book.getCopies() : Collections.emptyList();
                    int availableCount = 0;
                    for (BookCopy copy : copies) {
                        boolean hasActiveCheckout = copy.getId() != null && checkedOutCopyIds.contains(copy.getId());
                        if (!hasActiveCheckout && copy.getEffectiveAvailability() == BookCopy.Availability.AVAILABLE) {
                            availableCount++;
                        }
                    }
                    List<String> barcodes = copies.stream()
                            .map(BookCopy::getBarcode)
                            .collect(Collectors.toList());
                    return new BookSummary(
                            book.getId(),
                            book.getTitle(),
                            book.getAuthor(),
                            book.getIsbn(),
                            copies.size(),
                            availableCount,
                            barcodes
                    );
                })
                .collect(Collectors.toList());
    }
}

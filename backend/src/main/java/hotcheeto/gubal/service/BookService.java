package hotcheeto.gubal.service;

import hotcheeto.gubal.entity.Book;
import hotcheeto.gubal.repository.BookRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookService {

    private final BookRepo bookRepo;

    public BookService(BookRepo bookRepo) {
        this.bookRepo = bookRepo;
    }

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
}

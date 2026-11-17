package hotcheeto.gubal.controller;

import hotcheeto.gubal.entity.Book;
import hotcheeto.gubal.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    public record BookDto(Long id, String title, String author, String isbn, int copyCount) {
        public static BookDto from(Book b) {
            int copies = b.getCopies() != null ? b.getCopies().size() : 0;
            return new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(), copies);
        }
    }

    @GetMapping("/search")
    public List<BookDto> search(
            @RequestParam(defaultValue = "title") String type,
            @RequestParam(defaultValue = "") String query) {
        return bookService.searchBooks(type, query).stream()
                .map(BookDto::from)
                .collect(Collectors.toList());
    }
}

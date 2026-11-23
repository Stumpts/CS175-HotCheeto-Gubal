package hotcheeto.gubal.controller;

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

    public record BookDto(
            Long id,
            String title,
            String author,
            String isbn,
            int copyCount,
            int availableCount,
            List<String> barcodes
    ) {
        public static BookDto from(BookService.BookSummary s) {
            return new BookDto(
                    s.id(),
                    s.title(),
                    s.author(),
                    s.isbn(),
                    s.copyCount(),
                    s.availableCount(),
                    s.barcodes()
            );
        }
    }

    @GetMapping("/search")
    public List<BookDto> search(
            @RequestParam(defaultValue = "title") String type,
            @RequestParam(defaultValue = "") String query) {
        return bookService.searchBookSummaries(type, query).stream()
                .map(BookDto::from)
                .collect(Collectors.toList());
    }
}

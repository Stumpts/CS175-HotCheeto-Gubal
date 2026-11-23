package hotcheeto.gubal.service;

import hotcheeto.gubal.entity.Account;
import hotcheeto.gubal.entity.Book;
import hotcheeto.gubal.entity.BookCopy;
import hotcheeto.gubal.entity.Checkout;
import hotcheeto.gubal.repository.AccountRepo;
import hotcheeto.gubal.repository.BookCopyRepo;
import hotcheeto.gubal.repository.CheckoutRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CheckoutService {

    private static final int DEFAULT_LOAN_DAYS = 14;

    private final AccountRepo accountRepo;
    private final BookCopyRepo bookCopyRepo;
    private final CheckoutRepo checkoutRepo;

    public CheckoutService(AccountRepo accountRepo, BookCopyRepo bookCopyRepo, CheckoutRepo checkoutRepo) {
        this.accountRepo = accountRepo;
        this.bookCopyRepo = bookCopyRepo;
        this.checkoutRepo = checkoutRepo;
    }

    public static class CirculationException extends RuntimeException {
        private final HttpStatus status;

        public CirculationException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    public record CopyStatusDto(
            Long copyId,
            String barcode,
            Long bookId,
            String title,
            String author,
            String isbn,
            boolean available,
            String availability,
            String borrowerUsername,
            String checkoutDate,
            String dueDate
    ) {}

    public record AccountCheckoutDto(
            Long checkoutId,
            String barcode,
            String title,
            String author,
            String checkoutDate,
            String dueDate
    ) {}

    @Transactional(readOnly = true)
    public List<CopyStatusDto> listAllCopies() {
        Map<Long, Checkout> activeByCopyId = new HashMap<>();
        for (Checkout checkout : checkoutRepo.findByReturnedIsNull()) {
            if (checkout.getBook() != null && checkout.getBook().getId() != null) {
                activeByCopyId.put(checkout.getBook().getId(), checkout);
            }
        }
        return bookCopyRepo.findAllByOrderByIdAsc().stream()
                .map(copy -> toCopyStatusDto(copy, activeByCopyId.get(copy.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CopyStatusDto lookupByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new CirculationException(HttpStatus.BAD_REQUEST, "Barcode or RFID tag is required.");
        }
        BookCopy copy = bookCopyRepo.findByBarcodeIgnoreCase(barcode.trim())
                .orElseThrow(() -> new CirculationException(
                        HttpStatus.NOT_FOUND,
                        "No book copy found for barcode: " + barcode.trim()
                ));
        Checkout activeCheckout = checkoutRepo.findFirstByBookAndReturnedIsNull(copy).orElse(null);
        return toCopyStatusDto(copy, activeCheckout);
    }

    @Transactional
    public CopyStatusDto checkoutCopy(String employeeUsername, String barcode, String borrowerUsername) {
        Account employee = requireEmployee(employeeUsername);
        if (barcode == null || barcode.isBlank()) {
            throw new CirculationException(HttpStatus.BAD_REQUEST, "Barcode or RFID tag is required.");
        }

        BookCopy copy = bookCopyRepo.findByBarcodeIgnoreCase(barcode.trim())
                .orElseThrow(() -> new CirculationException(
                        HttpStatus.NOT_FOUND,
                        "No book copy found for barcode: " + barcode.trim()
                ));

        Optional<Checkout> existingActive = checkoutRepo.findFirstByBookAndReturnedIsNull(copy);
        if (existingActive.isPresent() || copy.getEffectiveAvailability() == BookCopy.Availability.CHECKED_OUT) {
            throw new CirculationException(HttpStatus.CONFLICT, "This book copy is already checked out.");
        }

        Account borrower;
        if (borrowerUsername != null && !borrowerUsername.isBlank()) {
            borrower = accountRepo.findByUsername(borrowerUsername.trim())
                    .orElseThrow(() -> new CirculationException(
                            HttpStatus.NOT_FOUND,
                            "Borrower account not found: " + borrowerUsername.trim()
                    ));
        } else {
            borrower = employee;
        }

        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_YEAR, DEFAULT_LOAN_DAYS);
        Date dueDate = cal.getTime();

        Checkout checkout = Checkout.builder()
                .book(copy)
                .borrower(borrower)
                .checkoutDate(now)
                .dueDate(dueDate)
                .returned(null)
                .build();
        checkoutRepo.save(checkout);

        copy.setAvailability(BookCopy.Availability.CHECKED_OUT);
        bookCopyRepo.save(copy);

        return toCopyStatusDto(copy, checkout);
    }

    @Transactional
    public CopyStatusDto checkinCopy(String employeeUsername, String barcode) {
        requireEmployee(employeeUsername);
        if (barcode == null || barcode.isBlank()) {
            throw new CirculationException(HttpStatus.BAD_REQUEST, "Barcode or RFID tag is required.");
        }

        BookCopy copy = bookCopyRepo.findByBarcodeIgnoreCase(barcode.trim())
                .orElseThrow(() -> new CirculationException(
                        HttpStatus.NOT_FOUND,
                        "No book copy found for barcode: " + barcode.trim()
                ));

        Optional<Checkout> activeOpt = checkoutRepo.findFirstByBookAndReturnedIsNull(copy);
        if (activeOpt.isEmpty() && copy.getEffectiveAvailability() == BookCopy.Availability.AVAILABLE) {
            throw new CirculationException(HttpStatus.CONFLICT, "This book copy is already checked in.");
        }

        if (activeOpt.isPresent()) {
            Checkout active = activeOpt.get();
            active.setReturned(new Date());
            checkoutRepo.save(active);
        }

        copy.setAvailability(BookCopy.Availability.AVAILABLE);
        bookCopyRepo.save(copy);

        return toCopyStatusDto(copy, null);
    }

    @Transactional(readOnly = true)
    public List<AccountCheckoutDto> getAccountCheckouts(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        return checkoutRepo.findByBorrowerUsernameIgnoreCaseAndReturnedIsNull(username.trim()).stream()
                .map(c -> {
                    BookCopy copy = c.getBook();
                    Book book = copy.getBook();
                    return new AccountCheckoutDto(
                            c.getId(),
                            copy.getBarcode(),
                            book.getTitle(),
                            book.getAuthor(),
                            formatDate(c.getCheckoutDate()),
                            formatDate(c.getDueDate())
                    );
                })
                .collect(Collectors.toList());
    }

    private Account requireEmployee(String employeeUsername) {
        if (employeeUsername == null || employeeUsername.isBlank()) {
            throw new CirculationException(HttpStatus.UNAUTHORIZED, "Employee login is required.");
        }
        Account account = accountRepo.findByUsername(employeeUsername.trim())
                .orElseThrow(() -> new CirculationException(HttpStatus.UNAUTHORIZED, "Account not found."));
        if (account.getRole() != Account.Role.EMPLOYEE) {
            throw new CirculationException(
                    HttpStatus.FORBIDDEN,
                    "Employee privileges are required to check out or check in books."
            );
        }
        return account;
    }

    private CopyStatusDto toCopyStatusDto(BookCopy copy, Checkout activeCheckout) {
        Book book = copy.getBook();
        boolean isCheckedOut = activeCheckout != null
                || copy.getEffectiveAvailability() == BookCopy.Availability.CHECKED_OUT;
        BookCopy.Availability effective = isCheckedOut
                ? BookCopy.Availability.CHECKED_OUT
                : copy.getEffectiveAvailability();
        boolean available = effective == BookCopy.Availability.AVAILABLE;

        return new CopyStatusDto(
                copy.getId(),
                copy.getBarcode(),
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                available,
                effective.name(),
                activeCheckout != null && activeCheckout.getBorrower() != null
                        ? activeCheckout.getBorrower().getUsername()
                        : null,
                activeCheckout != null ? formatDate(activeCheckout.getCheckoutDate()) : null,
                activeCheckout != null ? formatDate(activeCheckout.getDueDate()) : null
        );
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
    }
}

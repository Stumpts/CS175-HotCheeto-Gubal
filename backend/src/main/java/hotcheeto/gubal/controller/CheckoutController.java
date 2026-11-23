package hotcheeto.gubal.controller;

import hotcheeto.gubal.service.CheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkouts")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    public record CheckoutRequest(String employeeUsername, String barcode, String borrowerUsername) {}

    public record CheckinRequest(String employeeUsername, String barcode) {}

    @GetMapping("/copies")
    public List<CheckoutService.CopyStatusDto> listCopies() {
        return checkoutService.listAllCopies();
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String barcode) {
        try {
            return ResponseEntity.ok(checkoutService.lookupByBarcode(barcode));
        } catch (CheckoutService.CirculationException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request) {
        try {
            CheckoutService.CopyStatusDto dto = checkoutService.checkoutCopy(
                    request.employeeUsername(),
                    request.barcode(),
                    request.borrowerUsername()
            );
            String message = "Checked out \"" + dto.title() + "\""
                    + (dto.borrowerUsername() != null ? " to " + dto.borrowerUsername() : "");
            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "copy", dto
            ));
        } catch (CheckoutService.CirculationException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/checkin")
    public ResponseEntity<?> checkin(@RequestBody CheckinRequest request) {
        try {
            CheckoutService.CopyStatusDto dto = checkoutService.checkinCopy(
                    request.employeeUsername(),
                    request.barcode()
            );
            String message = "Checked in \"" + dto.title() + "\"";
            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "copy", dto
            ));
        } catch (CheckoutService.CirculationException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/account/{username}")
    public List<CheckoutService.AccountCheckoutDto> getAccountCheckouts(@PathVariable String username) {
        return checkoutService.getAccountCheckouts(username);
    }
}

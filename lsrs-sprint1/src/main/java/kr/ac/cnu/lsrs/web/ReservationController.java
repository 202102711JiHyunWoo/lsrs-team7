package kr.ac.cnu.lsrs.web;
import jakarta.validation.Valid;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.service.ReservationService;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationService reservations;
    public ReservationController(ReservationService reservations) { this.reservations = reservations; }
    @PostMapping("/reservations")
    public ResponseEntity<ReservationView> reserve(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservations.reserve(user.id(), request));
    }
    @GetMapping("/me/reservations")
    public List<ReservationView> mine(@AuthenticationPrincipal CurrentUser user) { return reservations.mine(user.id()); }
    @GetMapping("/reservations/{id}")
    public ReservationView detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) { return reservations.detail(user.id(), id); }
    @PostMapping("/reservations/{id}/check-outs")
    public ReservationView checkOut(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return reservations.checkOut(user.id(), id);
    }

    @PostMapping("/reservations/{id}/cancellations")
    public ReservationView cancel(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long id) {
        return reservations.cancel(user.id(), id);
    }
}
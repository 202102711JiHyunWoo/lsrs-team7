package kr.ac.cnu.lsrs.web;
import jakarta.validation.Valid;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.repository.SeatRepository;
import kr.ac.cnu.lsrs.service.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminPolicyController {
    private final PolicyService policies;
    private final QrService qr;
    private final SeatRepository seats;
    public AdminPolicyController(PolicyService policies, QrService qr, SeatRepository seats) { this.policies = policies; this.qr = qr; this.seats = seats; }
    @GetMapping("/policies") public PolicyView current() { return policies.current(); }
    @PutMapping("/policies")
    public PolicyView changeMode(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PolicyModeRequest request) {
        return policies.changeMode(user.id(), request);
    }
    @GetMapping("/seats") public List<SeatView> listSeats() {
        return seats.findAllByOrderByCodeAsc().stream().map(seat -> new SeatView(seat.getId(), seat.getCode(), seat.getZone(), "", false)).toList();
    }
    @GetMapping(value = "/seats/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> seatQr(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.png(id));
    }
}

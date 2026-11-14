package kr.ac.cnu.lsrs.web;
import jakarta.validation.Valid;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.service.CheckInService;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations/{id}/check-ins")
public class CheckInController {
    private final CheckInService checkIns;
    public CheckInController(CheckInService checkIns) { this.checkIns = checkIns; }
    @PostMapping
    public ReservationView checkIn(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                                    @Valid @RequestBody CheckInRequest request) {
        return checkIns.checkIn(user.id(), id, request.qrToken());
    }
}

package kr.ac.cnu.lsrs.web;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.service.SeatQueryService;
import kr.ac.cnu.lsrs.web.ApiDtos.SeatsView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatQueryService seats;
    public SeatController(SeatQueryService seats) { this.seats = seats; }
    @GetMapping
    public SeatsView query(@AuthenticationPrincipal CurrentUser user, @RequestParam LocalDate date,
                           @RequestParam LocalTime start, @RequestParam LocalTime end) {
        return seats.query(user.id(), date, start, end);
    }
}

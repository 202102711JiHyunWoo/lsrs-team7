package kr.ac.cnu.lsrs.web;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.service.BusinessException;
import kr.ac.cnu.lsrs.service.SeatQueryService;
import kr.ac.cnu.lsrs.service.TimeRules;
import kr.ac.cnu.lsrs.web.ApiDtos.SeatsView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatQueryService seats;
    public SeatController(SeatQueryService seats) { this.seats = seats; }
    @GetMapping
    public SeatsView query(@AuthenticationPrincipal CurrentUser user, @RequestParam LocalDate date,
                           @RequestParam LocalTime start, @RequestParam String end) {
        Instant from = date.atTime(start).atZone(TimeRules.ZONE).toInstant();
        Instant until;
        if ("24:00".equals(end)) {
            until = date.plusDays(1).atStartOfDay(TimeRules.ZONE).toInstant();
        } else {
            try {
                until = date.atTime(LocalTime.parse(end)).atZone(TimeRules.ZONE).toInstant();
            } catch (DateTimeParseException e) {
                throw BusinessException.bad("올바른 종료 시각을 선택해 주세요.");
            }
        }

        return seats.query(user.id(), from, until);
    }
}

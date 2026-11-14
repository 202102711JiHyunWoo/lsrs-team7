package kr.ac.cnu.lsrs.service;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatQueryService {
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final PolicyService policies;
    private final Clock clock;
    public SeatQueryService(SeatRepository seats, ReservationRepository reservations, PolicyService policies, Clock clock) {
        this.seats = seats; this.reservations = reservations; this.policies = policies; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public SeatsView query(long userId, LocalDate date, LocalTime start, LocalTime end) {
        PolicyVersion policy = policies.currentEntity();
        Instant from = date.atTime(start).atZone(TimeRules.ZONE).toInstant();
        Instant until = date.atTime(end).atZone(TimeRules.ZONE).toInstant();
        TimeRules.validate(from, until, policy.getReservationUnitMinutes());
        Map<Long, List<Reservation>> grouped = reservations.findOverlapping(from, until, ReservationService.ACTIVE).stream()
                .collect(Collectors.groupingBy(reservation -> reservation.getSeat().getId()));
        List<SeatView> result = seats.findAllByOrderByCodeAsc().stream().map(seat -> {
            List<Reservation> active = grouped.getOrDefault(seat.getId(), List.of());
            String state = active.isEmpty() ? "AVAILABLE" : active.stream().anyMatch(r -> r.getUser().getId() == userId) ? "MINE" :
                    active.stream().anyMatch(r -> r.getStatus() == Reservation.Status.CHECKED_IN) ? "IN_USE" : "RESERVED";
            return new SeatView(seat.getId(), seat.getCode(), seat.getZone(), state, active.isEmpty());
        }).toList();
        return new SeatsView(result, (int) result.stream().filter(SeatView::available).count(), PolicyView.of(policy), clock.instant());
    }
}

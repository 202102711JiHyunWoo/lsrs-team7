package kr.ac.cnu.lsrs.service;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class ReservationService {
    public static final List<Reservation.Status> ACTIVE = List.of(Reservation.Status.RESERVED, Reservation.Status.CHECKED_IN);
    private final AppUserRepository users;
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final PolicyService policies;
    private final Clock clock;
    public ReservationService(AppUserRepository users, SeatRepository seats, ReservationRepository reservations,
                              PolicyService policies, Clock clock) {
        this.users = users; this.seats = seats; this.reservations = reservations; this.policies = policies; this.clock = clock;
    }
    private AppUser lockStudent(long userId) {
        AppUser user = users.findLockedById(userId).orElseThrow(BusinessException::missing);
        if (user.getStatus() != AppUser.Status.ACTIVE || user.getRole() != AppUser.Role.STUDENT)
            throw BusinessException.forbidden("이메일 인증을 완료한 학생만 좌석을 예약하고 이용할 수 있습니다.");
        return user;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView reserve(long userId, ReservationRequest request) {
        AppUser user = lockStudent(userId);
        Seat seat = seats.findLockedById(request.seatId()).orElseThrow(BusinessException::missing);
        Instant now = clock.instant();
        if (user.isRestricted(now))
            throw new BusinessException(HttpStatus.CONFLICT, "RESERVATION_RESTRICTED", "예약 제한 기간에는 새 예약을 만들 수 없습니다.", Map.of("restrictedUntil", user.getRestrictedUntil().toString()));
        PolicyVersion policy = policies.currentEntity();
        if (policy.getVersionNo() != request.policyVersion())
            throw BusinessException.conflict("POLICY_CHANGED", "운영 정책이 변경되었습니다. 좌석 현황을 새로 확인해 주세요.");
        Instant start = request.startAt().toInstant(), end = request.endAt().toInstant();
        TimeRules.validate(start, end, policy.getReservationUnitMinutes());
        if (start.isBefore(now)) throw BusinessException.bad("이미 지난 시각으로 예약할 수 없습니다.");
        if (Duration.between(start, end).toMinutes() > policy.getMaxMinutes())
            throw BusinessException.bad("현재 정책에서 최대 " + policy.getMaxMinutes() / 60 + "시간까지 예약할 수 있습니다.");
        // The stable user and seat rows serialize every conflicting write; overlap must be rechecked after both locks.
        if (reservations.countConflicts(userId, seat.getId(), start, end, ACTIVE) > 0)
            throw BusinessException.conflict("RESERVATION_CONFLICT", "같은 시간에 이미 예약된 좌석이거나, 내 다른 예약과 시간이 겹칩니다.");
        Reservation saved = reservations.saveAndFlush(new Reservation(user, seat, policy, start, end, now));
        return ReservationView.of(saved);
    }
    /** Obtain identifiers without loading a stale managed Reservation before waiting on locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation lockOwned(long userId, long reservationId) {
        ReservationRepository.OwnerReference reference = reservations.ownerReference(reservationId).orElseThrow(BusinessException::missing);
        if (reference.getUserId() != userId) throw BusinessException.forbidden("본인의 예약만 이용할 수 있습니다.");
        lockStudent(userId);
        seats.findLockedById(reference.getSeatId()).orElseThrow(BusinessException::missing);
        Reservation reservation = reservations.findLockedById(reservationId).orElseThrow(BusinessException::missing);
        if (reservation.getUser().getId() != userId) throw BusinessException.forbidden("본인의 예약만 이용할 수 있습니다.");
        return reservation;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView checkOut(long userId, long reservationId) {
        Reservation reservation = lockOwned(userId, reservationId);
        if (reservation.getStatus() != Reservation.Status.CHECKED_IN)
            throw BusinessException.conflict("INVALID_RESERVATION_STATE", "이용 중인 예약만 퇴실할 수 있습니다. 이미 종료했는지 확인해 주세요.");
        reservation.complete(clock.instant());
        return ReservationView.of(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView cancel(long userId, long reservationId) {
        Reservation reservation = lockOwned(userId, reservationId);
        if (reservation.getStatus() == Reservation.Status.CANCELLED)
            return ReservationView.of(reservation);
        if (reservation.getStatus() != Reservation.Status.RESERVED)
            throw BusinessException.conflict("INVALID_RESERVATION_STATE", "입실 대기 중인 본인 예약만 취소할 수 있습니다. 이용 중이면 퇴실 기능을 사용해 주세요.");
        Instant now = clock.instant();
        if (now.isAfter(reservation.getCheckInDeadlineAt()) || !now.isBefore(reservation.getEndAt()))
            throw BusinessException.conflict("CANCELLATION_EXPIRED", "입실 기한이 지난 예약은 취소할 수 없습니다.");
        reservation.cancel();
        return ReservationView.of(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationView> mine(long userId) {
        return reservations.findByUserIdOrderByStartAtDescIdDesc(userId).stream().map(ReservationView::of).toList();
    }
    
    @Transactional(readOnly = true)
    public ReservationView detail(long userId, long reservationId) {
        Reservation reservation = reservations.findById(reservationId).orElseThrow(BusinessException::missing);
        if (reservation.getUser().getId() != userId) throw BusinessException.forbidden("본인의 예약만 조회할 수 있습니다.");
        return ReservationView.of(reservation);
    }
}

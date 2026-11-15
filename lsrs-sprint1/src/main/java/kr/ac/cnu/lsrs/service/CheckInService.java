package kr.ac.cnu.lsrs.service;

import kr.ac.cnu.lsrs.domain.Reservation;
import kr.ac.cnu.lsrs.web.ApiDtos.ReservationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service
public class CheckInService {
    private final ReservationService reservations;
    private final Clock clock;
    public CheckInService(ReservationService reservations, Clock clock) { this.reservations = reservations; this.clock = clock; }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationView checkIn(long userId, long reservationId, String qrToken) {
        Reservation reservation = reservations.lockOwned(userId, reservationId);
        if (!TokenHash.matches(qrToken, reservation.getSeat().getQrTokenHash()))
            throw BusinessException.conflict("QR_MISMATCH", "예약한 좌석의 QR 코드가 아닙니다. 좌석 번호를 확인해 주세요.");
        if (reservation.getStatus() == Reservation.Status.CHECKED_IN) return ReservationView.of(reservation);
        if (reservation.getStatus() != Reservation.Status.RESERVED)
            throw BusinessException.conflict("INVALID_RESERVATION_STATE", "종료되었거나 취소된 예약에는 체크인할 수 없습니다.");
        Instant now = clock.instant();
        if (now.isBefore(reservation.getStartAt()))
            throw BusinessException.conflict("CHECKIN_NOT_STARTED", "예약 시작 시각부터 체크인할 수 있습니다.");
        if (!now.isBefore(reservation.getEndAt()))
            throw BusinessException.conflict("CHECKIN_EXPIRED", "예약 이용 종료 시각이 지나 체크인할 수 없습니다.");
        if (now.isAfter(reservation.getCheckInDeadlineAt()))
            throw BusinessException.conflict("CHECKIN_EXPIRED", "입실 유예시간(15분)이 지나 체크인할 수 없습니다.");
        reservation.checkIn(now);
        return ReservationView.of(reservation);
    }
}

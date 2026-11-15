package kr.ac.cnu.lsrs;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.service.BusinessException;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationIntegrationTest extends IntegrationSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void US06_validReservationPersistsAndSnapshotsStartBasedDeadline() throws Exception {
        AppUser student = firstUser();
        send("/api/reservations", token(student), futureRequest(seat(0))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.checkInDeadlineAt").value("2026-11-14T01:45:00Z"));
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void US06_adminCannotCreateStudentReservation() throws Exception {
        send("/api/reservations", token(admin()), futureRequest(seat(0))).andExpect(status().isForbidden());
    }
    @Test void US06_pastStartIsRejected() throws Exception {
        send("/api/reservations", token(firstUser()), request(seat(0), "2026-11-14T09:30:00+09:00", "2026-11-14T10:30:00+09:00"))
                .andExpect(status().isBadRequest());
    }
    @Test void US06_midnightEndIsAllowedAndNextDayReservationIsAdjacent() throws Exception {
        String jwt = token(firstUser());
        read("/api/seats?date=2026-11-14&start=23:30&end=24:00", jwt).andExpect(status().isOk());
        send("/api/reservations", jwt, request(seat(0), "2026-11-14T23:30:00+09:00", "2026-11-15T00:00:00+09:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endAt").value("2026-11-14T15:00:00Z"));
        send("/api/reservations", jwt, request(seat(0), "2026-11-15T00:00:00+09:00", "2026-11-15T00:30:00+09:00"))
                .andExpect(status().isCreated());
    }
    @Test void US03_invalidMidnightQueryIsRejected() throws Exception {
        String jwt = token(firstUser());
        read("/api/seats?date=2026-11-14&start=23:30&end=24:30", jwt).andExpect(status().isBadRequest());
        read("/api/seats?date=2026-11-14&start=23:30&end=00:30", jwt).andExpect(status().isBadRequest());
    }
    @ParameterizedTest(name = "invalid interval {0} to {1}")
    @CsvSource({
            "2026-11-14T11:30:00+09:00,2026-11-14T10:30:00+09:00",
            "2026-11-14T10:30:00+09:00,2026-11-14T10:30:00+09:00",
            "2026-11-14T23:30:00+09:00,2026-11-15T00:30:00+09:00",
            "2026-11-14T10:15:00+09:00,2026-11-14T11:30:00+09:00",
            "2026-11-14T10:30:01+09:00,2026-11-14T11:30:00+09:00",
            "2026-11-14T10:30:00.001+09:00,2026-11-14T11:30:00+09:00",
            "2026-11-14T10:30:00+09:00,2026-11-14T15:00:00+09:00"})
    void US06_invalidIntervalsNeverPersist(String start, String end) throws Exception {
        send("/api/reservations", token(firstUser()), request(seat(0), start, end)).andExpect(status().isBadRequest());
        assertThat(reservations.count()).isZero();
    }
    @Test void US04_exactNormalMaximumFourHoursIsAllowed() throws Exception {
        send("/api/reservations", token(firstUser()), request(seat(0), "2026-11-14T10:30:00+09:00", "2026-11-14T14:30:00+09:00"))
                .andExpect(status().isCreated());
    }
    @Test void US06_missingSeatIs404() throws Exception {
        ReservationRequest body = new ReservationRequest(999999L, OffsetDateTime.parse("2026-11-14T10:30:00+09:00"),
                OffsetDateTime.parse("2026-11-14T11:30:00+09:00"), policies.current().versionNo());
        send("/api/reservations", token(firstUser()), body).andExpect(status().isNotFound());
    }
    @Test void US07_sameStudentCannotReserveOverlappingDifferentSeats() throws Exception {
        AppUser student = firstUser(); String jwt = token(student);
        send("/api/reservations", jwt, futureRequest(seat(0))).andExpect(status().isCreated());
        send("/api/reservations", jwt, futureRequest(seat(1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void US07_differentStudentCannotReserveSameSeatOverlap() throws Exception {
        AppUser first = firstUser(), second = secondUser();
        reservationService.reserve(first.getId(), futureRequest(seat(0)));
        send("/api/reservations", token(second), futureRequest(seat(0))).andExpect(status().isConflict());
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void US07_adjacentReservationsAreAllowedForSameStudent() throws Exception {
        AppUser student = firstUser(); reservationService.reserve(student.getId(), futureRequest(seat(0)));
        send("/api/reservations", token(student), request(seat(1), "2026-11-14T11:30:00+09:00", "2026-11-14T12:30:00+09:00"))
                .andExpect(status().isCreated());
    }
    @Test void US07_adjacentReservationsAreAllowedForSameSeat() throws Exception {
        AppUser first = firstUser(), second = secondUser(); reservationService.reserve(first.getId(), futureRequest(seat(0)));
        send("/api/reservations", token(second), request(seat(0), "2026-11-14T11:30:00+09:00", "2026-11-14T12:30:00+09:00"))
                .andExpect(status().isCreated());
    }
    @Test void reservationRestrictionIsEnforcedBeforeSprint2AutomaticPenalties() throws Exception {
        AppUser student = firstUser(); student.restrictUntil(clock.instant().plusSeconds(86400)); users.saveAndFlush(student);
        send("/api/reservations", token(student), futureRequest(seat(0))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_RESTRICTED"))
                .andExpect(jsonPath("$.details.restrictedUntil").exists());
        assertThat(reservations.count()).isZero();
    }
    @Test void restrictionEndsExactlyAtItsDeadline() throws Exception {
        AppUser student = firstUser(); student.restrictUntil(clock.instant()); users.saveAndFlush(student);
        send("/api/reservations", token(student), futureRequest(seat(0))).andExpect(status().isCreated());
    }
    @Test void US03_gridContainsAllFourDisplayStatesWithoutPrivateData() throws Exception {
        AppUser first = firstUser(), second = secondUser(), third = user("20260003", "third@example.test");
        reservationService.reserve(first.getId(), currentRequest(seat(0)));
        ReservationView checked = reservationService.reserve(second.getId(), currentRequest(seat(1)));
        checkInService.checkIn(second.getId(), checked.id(), qr.token(seat(1).getCode()));
        reservationService.reserve(third.getId(), currentRequest(seat(2)));
        String body = content(read("/api/seats?date=2026-11-14&start=10:00&end=11:00", token(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats[0].displayStatus").value("MINE"))
                .andExpect(jsonPath("$.seats[1].displayStatus").value("IN_USE"))
                .andExpect(jsonPath("$.seats[2].displayStatus").value("RESERVED"))
                .andExpect(jsonPath("$.seats[3].displayStatus").value("AVAILABLE")));
        assertThat(body).doesNotContain("first@example", "second@example", "third@example", "passwordHash", "qrTokenHash", "userId");
    }
    @Test void US03_fullyBookedGridReportsZeroAvailability() throws Exception {
        AppUser viewer = firstUser();
        int index = 100;
        for (Seat available : seats.findAllByOrderByCodeAsc()) {
            AppUser owner = user("2026%04d".formatted(index), "full" + index + "@example.test");
            reservationService.reserve(owner.getId(), currentRequest(available));
            index++;
        }
        read("/api/seats?date=2026-11-14&start=10:00&end=11:00", token(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableCount").value(0))
                .andExpect(jsonPath("$.seats.length()").value(48))
                .andExpect(jsonPath("$.seats[?(@.available == true)]").isEmpty());
    }
    @Test void US03_invalidQueryReturns400NotAvailableSeats() throws Exception {
        read("/api/seats?date=2026-11-14&start=11:00&end=10:00", token(firstUser()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.seats").doesNotExist());
    }
    @Test void US03_missingOrMalformedQueryIsRejected() throws Exception {
        String jwt = token(firstUser());
        read("/api/seats?date=broken&start=10:00&end=11:00", jwt).andExpect(status().isBadRequest());
        read("/api/seats?date=2026-11-14", jwt).andExpect(status().isBadRequest());
    }
    @Test void US02_otherStudentCannotReadReservationDetail() throws Exception {
        AppUser first = firstUser(), second = secondUser();
        ReservationView reservation = reservationService.reserve(first.getId(), currentRequest(seat(0)));
        read("/api/reservations/" + reservation.id(), token(second)).andExpect(status().isForbidden());
        read("/api/me/reservations", token(second)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void US12_generatedPngDecodesAndChecksInReservedSeat() throws Exception {
        AppUser student = firstUser(), admin = admin(); Seat seat = seat(0);
        ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat));
        byte[] image = read("/api/admin/seats/" + seat.getId() + "/qr.png", token(admin)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var luminance = new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(image)));
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(luminance))).getText();
        assertThat(decoded).startsWith("LSRS1|" + seat.getCode() + "|");
        send("/api/reservations/" + reservation.id() + "/check-ins", token(student), new CheckInRequest(decoded.split("\\|")[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }
    @Test void US12_wrongQrDoesNotChangeReservationState() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        send("/api/reservations/" + reservation.id() + "/check-ins", token(student), new CheckInRequest("invalid"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_MISMATCH"));
        assertThat(reservations.findById(reservation.id()).orElseThrow().getStatus()).isEqualTo(Reservation.Status.RESERVED);
    }
    @Test void US12_validQrForDifferentSeatIsRejected() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        send("/api/reservations/" + reservation.id() + "/check-ins", token(student), new CheckInRequest(qr.token(seat(1).getCode())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_MISMATCH"));
    }
    @Test void US12_otherStudentCannotCheckInReservation() throws Exception {
        AppUser first = firstUser(), second = secondUser(); ReservationView reservation = reservationService.reserve(first.getId(), currentRequest(seat(0)));
        send("/api/reservations/" + reservation.id() + "/check-ins", token(second), new CheckInRequest(qr.token(seat(0).getCode())))
                .andExpect(status().isForbidden());
    }
    @Test void US12_checkInBeforeStartIsRejected() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), futureRequest(seat(0)));
        send("/api/reservations/" + reservation.id() + "/check-ins", token(student), new CheckInRequest(qr.token(seat(0).getCode())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKIN_NOT_STARTED"));
    }
    @Test void US12_exactDeadlineIsAllowed() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        String jwt = token(student); clock.advance(Duration.ofMinutes(15));
        send("/api/reservations/" + reservation.id() + "/check-ins", jwt, new CheckInRequest(qr.token(seat(0).getCode())))
                .andExpect(status().isOk());
    }
    @Test void US12_oneNanosecondAfterDeadlineIsRejectedWithoutScheduler() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        String jwt = token(student); clock.advance(Duration.ofMinutes(15).plusNanos(1));
        send("/api/reservations/" + reservation.id() + "/check-ins", jwt, new CheckInRequest(qr.token(seat(0).getCode())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKIN_EXPIRED"));
        assertThat(reservations.findById(reservation.id()).orElseThrow().getStatus()).isEqualTo(Reservation.Status.RESERVED);
    }
    @Test void US12_duplicateCheckInIsIdempotentEvenAfterGraceWindow() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        String jwt = token(student); String path = "/api/reservations/" + reservation.id() + "/check-ins";
        String first = content(send(path, jwt, new CheckInRequest(qr.token(seat(0).getCode()))).andExpect(status().isOk()));
        clock.advance(Duration.ofMinutes(20));
        String second = content(send(path, jwt, new CheckInRequest(qr.token(seat(0).getCode()))).andExpect(status().isOk()));
        assertThat(value(first, "$.checkedInAt")).isEqualTo(value(second, "$.checkedInAt"));
        assertThat(users.findById(student.getId()).orElseThrow().getNoShowCount()).isZero();
    }
    @Test void US12_autoCancelledReservationCannotBeCheckedIn() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        jdbc.update("update reservation set status='AUTO_CANCELLED' where id=?", reservation.id());
        send("/api/reservations/" + reservation.id() + "/check-ins", token(student), new CheckInRequest(qr.token(seat(0).getCode())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
    }
    @Test void US10_reservedButNotCheckedInCannotExit() throws Exception {
        AppUser student = firstUser(); ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        send("/api/reservations/" + reservation.id() + "/check-outs", token(student), null).andExpect(status().isConflict());
    }
    @Test void US10_exitReleasesSeatAndKeepsOriginalReservationTime() throws Exception {
        AppUser student = firstUser(); Seat seat = seat(0);
        ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat));
        checkInService.checkIn(student.getId(), reservation.id(), qr.token(seat.getCode()));
        clock.advance(Duration.ofMinutes(10));
        send("/api/reservations/" + reservation.id() + "/check-outs", token(student), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endAt").value("2026-11-14T02:00:00Z"));
        read("/api/seats?date=2026-11-14&start=10:30&end=11:00", token(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats[0].displayStatus").value("AVAILABLE"));
    }
    @Test void US10_exitPreservesFollowingStudentsReservation() throws Exception {
        AppUser first = firstUser(), second = secondUser(); Seat seat = seat(0);
        ReservationView current = reservationService.reserve(first.getId(), currentRequest(seat));
        ReservationView following = reservationService.reserve(second.getId(), request(seat, "2026-11-14T11:00:00+09:00", "2026-11-14T12:00:00+09:00"));
        checkInService.checkIn(first.getId(), current.id(), qr.token(seat.getCode()));
        reservationService.checkOut(first.getId(), current.id());
        read("/api/seats?date=2026-11-14&start=11:00&end=12:00", token(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats[0].displayStatus").value("RESERVED"));
        assertThat(reservations.findById(following.id()).orElseThrow().getStatus()).isEqualTo(Reservation.Status.RESERVED);
    }
    @Test void US10_otherStudentCannotExitAndDuplicateExitIsRejected() throws Exception {
        AppUser first = firstUser(), second = secondUser(); ReservationView reservation = reservationService.reserve(first.getId(), currentRequest(seat(0)));
        checkInService.checkIn(first.getId(), reservation.id(), qr.token(seat(0).getCode()));
        send("/api/reservations/" + reservation.id() + "/check-outs", token(second), null).andExpect(status().isForbidden());
        send("/api/reservations/" + reservation.id() + "/check-outs", token(first), null).andExpect(status().isOk());
        Instant completed = reservations.findById(reservation.id()).orElseThrow().getCompletedAt();
        clock.advance(Duration.ofMinutes(1));
        send("/api/reservations/" + reservation.id() + "/check-outs", token(first), null).andExpect(status().isConflict());
        assertThat(reservations.findById(reservation.id()).orElseThrow().getCompletedAt()).isEqualTo(completed);
    }
    @Test void US12_completedReservationCannotBeCheckedInAgain() throws Exception {
        AppUser first = firstUser(); ReservationView reservation = reservationService.reserve(first.getId(), currentRequest(seat(0)));
        checkInService.checkIn(first.getId(), reservation.id(), qr.token(seat(0).getCode())); reservationService.checkOut(first.getId(), reservation.id());
        send("/api/reservations/" + reservation.id() + "/check-ins", token(first), new CheckInRequest(qr.token(seat(0).getCode()))).andExpect(status().isConflict());
    }
}

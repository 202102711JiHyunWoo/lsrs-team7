package kr.ac.cnu.lsrs;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.service.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.assertj.core.api.Assertions.*;

@Timeout(60)
class ConcurrencyIntegrationTest extends IntegrationSupport {
    record Outcome(boolean success, String code) { }
    private List<Outcome> compete(int requests, IntFunction<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests), start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                int value = index;
                futures.add(pool.submit(() -> {
                    ready.countDown(); if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    try { action.apply(value); return new Outcome(true, "SUCCESS"); }
                    catch (BusinessException error) { return new Outcome(false, error.getCode()); }
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Outcome> results = new ArrayList<>();
            for (Future<Outcome> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @RepeatedTest(3)
    void US07_twentyStudentsCompeteForOneSeat_onlyOneCommittedReservation() throws Exception {
        List<AppUser> students = new ArrayList<>();
        for (int index = 0; index < 20; index++) students.add(user("2026%04d".formatted(index + 10), "race" + index + "@example.test"));
        var request = futureRequest(seat(0));
        List<Outcome> outcomes = compete(20, index -> reservationService.reserve(students.get(index).getId(), request));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(result -> !result.success()).map(Outcome::code)).containsOnly("RESERVATION_CONFLICT");
        assertThat(reservations.count()).isEqualTo(1);
        System.out.println("US07_SAME_SEAT requests=20 success=1 conflicts=19 duplicate_allocations=0");
    }
    @RepeatedTest(3)
    void US07_oneStudentCompetesForTwentyDifferentSeats_onlyOneCommittedReservation() throws Exception {
        AppUser student = firstUser(); List<Seat> available = seats.findAllByOrderByCodeAsc();
        List<Outcome> outcomes = compete(20, index -> reservationService.reserve(student.getId(), futureRequest(available.get(index))));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(result -> !result.success()).map(Outcome::code)).containsOnly("RESERVATION_CONFLICT");
        assertThat(reservations.count()).isEqualTo(1);
        System.out.println("US07_SAME_STUDENT requests=20 success=1 conflicts=19 overlapping_reservations=0");
    }
    @Test void US12_tenConcurrentCheckInsRemainIdempotent() throws Exception {
        AppUser student = firstUser(); Seat seat = seat(0);
        ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat));
        String token = qr.token(seat.getCode());
        List<Outcome> outcomes = compete(10, index -> checkInService.checkIn(student.getId(), reservation.id(), token));
        assertThat(outcomes).allMatch(Outcome::success);
        Reservation stored = reservations.findById(reservation.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Reservation.Status.CHECKED_IN);
        assertThat(stored.getCheckedInAt()).isEqualTo(clock.instant());
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void US10_tenConcurrentCheckOutsCompleteOnlyOnce() throws Exception {
        AppUser student = firstUser(); Seat seat = seat(0);
        ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat));
        checkInService.checkIn(student.getId(), reservation.id(), qr.token(seat.getCode()));
        List<Outcome> outcomes = compete(10, index -> reservationService.checkOut(student.getId(), reservation.id()));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(result -> !result.success()).map(Outcome::code)).containsOnly("INVALID_RESERVATION_STATE");
        assertThat(reservations.findById(reservation.id()).orElseThrow().getStatus()).isEqualTo(Reservation.Status.COMPLETED);
    }
    @RepeatedTest(3)
    void concurrentIdempotentCheckInAndCheckOutNeverResurrectCompletedReservation() throws Exception {
        AppUser student = firstUser(); Seat seat = seat(0);
        ReservationView reservation = reservationService.reserve(student.getId(), currentRequest(seat));
        String token = qr.token(seat.getCode()); checkInService.checkIn(student.getId(), reservation.id(), token);
        List<Outcome> outcomes = compete(2, index -> index == 0 ? checkInService.checkIn(student.getId(), reservation.id(), token) : reservationService.checkOut(student.getId(), reservation.id()));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isGreaterThanOrEqualTo(1);
        assertThat(reservations.findById(reservation.id()).orElseThrow().getStatus()).isEqualTo(Reservation.Status.COMPLETED);
        assertThat(users.findById(student.getId()).orElseThrow().getNoShowCount()).isZero();
    }
    @Test void US01_concurrentVerificationConsumesTokenOnlyOnce() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        String token = latestMailToken("new@example.test");
        List<Outcome> outcomes = compete(2, index -> auth.verify(token));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(result -> !result.success()).map(Outcome::code)).containsExactly("VERIFICATION_USED");
        assertThat(users.findByEmail("new@example.test").orElseThrow().getStatus()).isEqualTo(AppUser.Status.ACTIVE);
    }
    @Test void US04_concurrentPolicyChangesCannotOverwriteEachOther() throws Exception {
        AppUser firstAdmin = admin(); AppUser secondAdmin = user(null, "second-admin@example.test", AppUser.Role.ADMIN);
        List<Outcome> outcomes = compete(2, index -> policies.changeMode(index == 0 ? firstAdmin.getId() : secondAdmin.getId(), new PolicyModeRequest(PolicyVersion.Mode.EXAM, 1L)));
        assertThat(outcomes.stream().filter(Outcome::success).count()).isEqualTo(1);
        assertThat(policyRows.count()).isEqualTo(2);
        assertThat(policies.current().versionNo()).isEqualTo(2);
    }
}

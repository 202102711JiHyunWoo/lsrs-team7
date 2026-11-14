package kr.ac.cnu.lsrs.web;

import jakarta.validation.constraints.*;
import kr.ac.cnu.lsrs.domain.*;
import java.time.*;
import java.util.*;

public final class ApiDtos {
    private ApiDtos() { }
    public record RegisterRequest(
            String studentNo,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "STUDENT|ADMIN") String requestedRole) { }
    public record LoginRequest(@NotBlank @Size(max = 254) String identifier,
                               @NotBlank @Size(max = 100) String password) { }
    public record VerificationRequest(@NotBlank @Size(max = 128) String token) { }
    public record ResendRequest(@NotBlank @Email @Size(max = 254) String email) { }
    public record ReservationRequest(@NotNull @Positive Long seatId,
                                     @NotNull OffsetDateTime startAt,
                                     @NotNull OffsetDateTime endAt,
                                     @NotNull @Positive Long policyVersion) { }
    public record CheckInRequest(@NotBlank @Size(max = 256) String qrToken) { }
    public record PolicyModeRequest(@NotNull PolicyVersion.Mode mode,
                                    @NotNull @Positive Long versionNo) { }
    public record UserView(Long id, String studentNo, String email, AppUser.Role role,
                           AppUser.Status status, Instant restrictedUntil) {
        public static UserView of(AppUser user) {
            return new UserView(user.getId(), user.getStudentNo(), user.getEmail(),
                    user.getRole(), user.getStatus(), user.getRestrictedUntil());
        }
    }
    public record RegistrationView(UserView user, String deliveryStatus, String message) { }
    public record LoginView(String accessToken, String tokenType, Instant expiresAt, UserView user) { }
    public record MessageView(String message) { }
    public record PolicyView(Long id, long versionNo, PolicyVersion.Mode mode, int maxMinutes,
                             int normalMaxMinutes, int examMaxMinutes, int reservationUnitMinutes,
                             int graceMinutes, int noShowThreshold, int restrictionDays) {
        public static PolicyView of(PolicyVersion policy) {
            return new PolicyView(policy.getId(), policy.getVersionNo(), policy.getMode(), policy.getMaxMinutes(),
                    policy.getNormalMaxMinutes(), policy.getExamMaxMinutes(), policy.getReservationUnitMinutes(),
                    policy.getGraceMinutes(), policy.getNoShowThreshold(), policy.getRestrictionDays());
        }
    }
    public record SeatView(Long id, String code, String zone, String displayStatus, boolean available) { }
    public record SeatsView(List<SeatView> seats, int availableCount, PolicyView policy, Instant serverTime) { }
    public record ReservationView(Long id, Long seatId, String seatCode, String zone,
                                  Instant startAt, Instant endAt, Instant checkInDeadlineAt,
                                  Instant checkedInAt, Instant completedAt, Reservation.Status status,
                                  long policyVersion) {
        public static ReservationView of(Reservation reservation) {
            return new ReservationView(reservation.getId(), reservation.getSeat().getId(),
                    reservation.getSeat().getCode(), reservation.getSeat().getZone(),
                    reservation.getStartAt(), reservation.getEndAt(), reservation.getCheckInDeadlineAt(),
                    reservation.getCheckedInAt(), reservation.getCompletedAt(), reservation.getStatus(),
                    reservation.getPolicy().getVersionNo());
        }
    }
    public record AppInfo(Instant serverTime, String timeZone, boolean demo, boolean localMailbox, int pollingSeconds) { }
    public record ErrorView(String code, String message, Map<String, String> fieldErrors,
                            Map<String, Object> details, String requestId) { }
}

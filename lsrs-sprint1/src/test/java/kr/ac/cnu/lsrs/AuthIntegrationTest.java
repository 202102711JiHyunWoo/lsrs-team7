package kr.ac.cnu.lsrs;

import kr.ac.cnu.lsrs.domain.AppUser;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationSupport {
    @Autowired JwtEncoder encoder;

    @Test void US01_signupCreatesPendingHashedAccountAndLocalEmail() throws Exception {
        String result = content(send("/api/users", null, signup("New@Example.Test", "20260011"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.status").value("PENDING"))
                .andExpect(jsonPath("$.deliveryStatus").value("LOCAL_MAILBOX")));
        AppUser stored = users.findByEmail("new@example.test").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(result).doesNotContain(PASSWORD, stored.getPasswordHash(), latestMailToken("new@example.test"));
        assertThat(stored.getStudentNo()).isEqualTo("20260011");
    }
    @Test void US01_normalizedDuplicateEmailIsRejected() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/users", null, signup("NEW@example.test", "20260012")).andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(1);
    }
    @Test void US01_duplicateStudentNumberIsRejected() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/users", null, signup("other@example.test", "20260011")).andExpect(status().isConflict());
    }
    @Test void US01_invalidEmailIsRejected() throws Exception {
        send("/api/users", null, signup("not-an-email", "20260011")).andExpect(status().isBadRequest());
    }
    @Test void US01_missingRequiredFieldsAreRejected() throws Exception {
        send("/api/users", null, Map.of("email", "new@example.test")).andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }
    @Test void US01_invalidStudentNumberIsRejected() throws Exception {
        send("/api/users", null, signup("new@example.test", "abc123")).andExpect(status().isBadRequest());
    }
    @Test void US01_weakPasswordIsRejected() throws Exception {
        send("/api/users", null, Map.of("email", "new@example.test", "studentNo", "20260011", "password", "password"))
                .andExpect(status().isBadRequest());
    }
    @Test void US01_bcryptByteLimitIsValidatedBeforeHashing() throws Exception {
        send("/api/users", null, Map.of("email", "new@example.test", "studentNo", "20260011", "password", "Ab1!" + "가".repeat(24)))
                .andExpect(status().isBadRequest());
    }
    @Test void US02_arbitraryAdminSignupIsForbidden() throws Exception {
        send("/api/users", null, Map.of("email", "attacker@example.test", "password", PASSWORD, "requestedRole", "ADMIN"))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
    }
    @Test void US01_allowlistedAdminStillRequiresEmailVerification() throws Exception {
        send("/api/users", null, Map.of("email", "admin@example.test", "password", PASSWORD, "requestedRole", "ADMIN"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("ADMIN"));
        send("/api/auth/tokens", null, new LoginRequest("admin@example.test", PASSWORD)).andExpect(status().isForbidden());
        send("/api/email-verifications", null, new VerificationRequest(latestMailToken("admin@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test void US01_validVerificationActivatesAccount() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/email-verifications", null, new VerificationRequest(latestMailToken("new@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test void US01_unverifiedAccountCannotLogin() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/auth/tokens", null, new LoginRequest("new@example.test", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_UNVERIFIED"));
    }
    @Test void US01_invalidTokenDoesNotActivateAccount() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/email-verifications", null, new VerificationRequest("invalid"))
                .andExpect(status().isBadRequest());
        assertThat(users.findByEmail("new@example.test").orElseThrow().getStatus()).isEqualTo(AppUser.Status.PENDING);
    }
    @Test void US01_tokenExpiresExactlyAtDeadline() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        String mailToken = latestMailToken("new@example.test"); clock.advance(Duration.ofMinutes(30));
        send("/api/email-verifications", null, new VerificationRequest(mailToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }
    @Test void US01_usedTokenCannotBeReused() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        var body = new VerificationRequest(latestMailToken("new@example.test"));
        send("/api/email-verifications", null, body).andExpect(status().isOk());
        send("/api/email-verifications", null, body).andExpect(status().isConflict());
    }
    @Test void US01_resendInvalidatesPreviousToken() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        String oldToken = latestMailToken("new@example.test"); clock.advance(Duration.ofSeconds(61));
        send("/api/email-verifications/resend", null, new ResendRequest("new@example.test")).andExpect(status().isOk());
        String nextToken = latestMailToken("new@example.test"); assertThat(nextToken).isNotEqualTo(oldToken);
        send("/api/email-verifications", null, new VerificationRequest(oldToken)).andExpect(status().isConflict());
        send("/api/email-verifications", null, new VerificationRequest(nextToken)).andExpect(status().isOk());
    }
    @Test void US01_resendHasOneMinuteCooldown() throws Exception {
        send("/api/users", null, signup("new@example.test", "20260011")).andExpect(status().isCreated());
        send("/api/email-verifications/resend", null, new ResendRequest("new@example.test")).andExpect(status().isTooManyRequests());
    }
    @Test void US01_unknownResendDoesNotExposeAccountExistence() throws Exception {
        send("/api/email-verifications/resend", null, new ResendRequest("unknown@example.test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
        assertThat(verifications.count()).isZero();
    }
    @Test void US02_loginAcceptsEmailAndStudentNumber() throws Exception {
        AppUser student = firstUser();
        for (String identifier : List.of(student.getStudentNo(), student.getEmail())) {
            send("/api/auth/tokens", null, new LoginRequest(identifier, PASSWORD))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.user.role").value("STUDENT"));
        }
    }
    @Test void US02_wrongPasswordIsRejected() throws Exception {
        firstUser(); send("/api/auth/tokens", null, new LoginRequest("first@example.test", "Incorrect123!"))
                .andExpect(status().isUnauthorized());
    }
    @Test void US02_unknownAccountIsRejected() throws Exception {
        send("/api/auth/tokens", null, new LoginRequest("missing@example.test", PASSWORD)).andExpect(status().isUnauthorized());
    }
    @Test void US02_missingAuthenticationIsRejected() throws Exception {
        read("/api/seats?date=2026-11-14&start=10:00&end=11:00", null).andExpect(status().isUnauthorized());
    }
    @Test void US02_forgedTokenIsRejected() throws Exception {
        String jwt = token(firstUser());
        String[] segments = jwt.split("\\.");
        segments[1] = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes());
        read("/api/me", String.join(".", segments)).andExpect(status().isUnauthorized());
    }
    @Test void US02_expiredTokenIsRejected() throws Exception {
        String jwt = token(firstUser()); clock.advance(Duration.ofMinutes(61));
        read("/api/me", jwt).andExpect(status().isUnauthorized());
    }
    @Test void US02_tokenExpiresExactlyAtSixtyMinutes() throws Exception {
        String jwt = token(firstUser()); clock.advance(Duration.ofMinutes(60));
        read("/api/me", jwt).andExpect(status().isUnauthorized());
    }
    @Test void US02_signedTokenWithoutExpiryIsRejected() throws Exception {
        AppUser student = firstUser();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("lsrs-team7").subject(student.getId().toString())
                .issuedAt(clock.instant()).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        read("/api/me", jwt).andExpect(status().isUnauthorized());
    }
    @Test void unknownJsonPropertiesAreRejected() throws Exception {
        send("/api/users", null, Map.of("email", "new@example.test", "studentNo", "20260011",
                "password", PASSWORD, "role", "ADMIN")).andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }
    @Test void US02_wrongIssuerIsRejected() throws Exception {
        AppUser student = firstUser();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("wrong-issuer").subject(student.getId().toString())
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(60)).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        read("/api/me", jwt).andExpect(status().isUnauthorized());
    }
    @Test void US02_studentCannotReadOrChangeAdminPolicies() throws Exception {
        String jwt = token(firstUser());
        read("/api/admin/policies", jwt).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/policies")
                .header("Authorization", "Bearer " + jwt).contentType("application/json").content("{\"mode\":\"EXAM\",\"versionNo\":1}"))
                .andExpect(status().isForbidden());
        assertThat(policies.current().mode().name()).isEqualTo("NORMAL");
    }
    @Test void US02_adminCanReadPoliciesAndOwnIdentity() throws Exception {
        AppUser admin = admin(); String jwt = token(admin);
        read("/api/admin/policies", jwt).andExpect(status().isOk());
        read("/api/me", jwt).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("admin@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void US02_studentCannotDownloadSeatQrSecrets() throws Exception {
        read("/api/admin/seats/" + seat(0).getId() + "/qr.png", token(firstUser())).andExpect(status().isForbidden());
    }
    @Test void malformedJsonReturnsStructured400InsteadOf500() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users")
                .contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}

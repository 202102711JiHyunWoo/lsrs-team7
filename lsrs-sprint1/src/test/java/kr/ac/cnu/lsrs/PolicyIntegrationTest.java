package kr.ac.cnu.lsrs;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PolicyIntegrationTest extends IntegrationSupport {
    @Test void US04_normalPolicyIsVisibleToAuthenticatedStudent() throws Exception {
        read("/api/policies/current", token(firstUser())).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL")).andExpect(jsonPath("$.maxMinutes").value(240))
                .andExpect(jsonPath("$.reservationUnitMinutes").value(30)).andExpect(jsonPath("$.graceMinutes").value(15));
    }
    @Test void US04_adminModeSwitchChangesBothDisplayedPolicyAndReservationValidation() throws Exception {
        AppUser admin = admin(), student = firstUser();
        policies.changeMode(admin.getId(), new PolicyModeRequest(PolicyVersion.Mode.EXAM, 1L));
        String jwt = token(student);
        read("/api/policies/current", jwt).andExpect(status().isOk()).andExpect(jsonPath("$.maxMinutes").value(180));
        send("/api/reservations", jwt, request(seat(0), "2026-11-14T10:30:00+09:00", "2026-11-14T14:00:00+09:00"))
                .andExpect(status().isBadRequest());
        send("/api/reservations", jwt, request(seat(0), "2026-11-14T10:30:00+09:00", "2026-11-14T13:30:00+09:00"))
                .andExpect(status().isCreated());
    }
    @Test void US04_staleClientPolicyCannotCreateReservation() throws Exception {
        AppUser admin = admin(), student = firstUser(); var oldRequest = futureRequest(seat(0));
        policies.changeMode(admin.getId(), new PolicyModeRequest(PolicyVersion.Mode.EXAM, 1L));
        send("/api/reservations", token(student), oldRequest).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
    }
    @Test void US04_currentModeUpdateIsIdempotentAndOldVersionIsRejected() throws Exception {
        AppUser admin = admin(); String jwt = token(admin);
        mvc.perform(put("/api/admin/policies").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content(json.writeValueAsString(new PolicyModeRequest(PolicyVersion.Mode.EXAM, 1L)))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/policies").header("Authorization", "Bearer " + jwt).contentType("application/json")
                .content(json.writeValueAsString(new PolicyModeRequest(PolicyVersion.Mode.NORMAL, 1L)))).andExpect(status().isConflict());
        assertThat(policies.changeMode(admin.getId(), new PolicyModeRequest(PolicyVersion.Mode.EXAM, 2L)).versionNo()).isEqualTo(2);
        assertThat(policyRows.count()).isEqualTo(2);
    }
    @Test void US04_modeChangesDoNotModifyExistingCheckInDeadline() {
        AppUser admin = admin(), student = firstUser(); var reservation = reservationService.reserve(student.getId(), currentRequest(seat(0)));
        policies.changeMode(admin.getId(), new PolicyModeRequest(PolicyVersion.Mode.EXAM, 1L));
        assertThat(reservations.findById(reservation.id()).orElseThrow().getCheckInDeadlineAt()).isEqualTo(reservation.checkInDeadlineAt());
        assertThat(reservations.findById(reservation.id()).orElseThrow().getPolicy().getId()).isNotNull();
    }
    @Test void US04_invalidModeIsRejectedWithoutChangingPolicy() throws Exception {
        mvc.perform(put("/api/admin/policies").header("Authorization", "Bearer " + token(admin())).contentType("application/json")
                .content("{\"mode\":\"UNKNOWN\",\"versionNo\":1}")).andExpect(status().isBadRequest());
        assertThat(policyRows.count()).isEqualTo(1);
    }
}

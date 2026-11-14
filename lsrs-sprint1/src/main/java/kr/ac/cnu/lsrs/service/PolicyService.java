package kr.ac.cnu.lsrs.service;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;

@Service
public class PolicyService {
    private final PolicyVersionRepository policies;
    private final AppUserRepository users;
    private final Clock clock;
    public PolicyService(PolicyVersionRepository policies, AppUserRepository users, Clock clock) {
        this.policies = policies; this.users = users; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public PolicyVersion currentEntity() {
        return policies.findFirstByOrderByVersionNoDesc().orElseThrow(() -> new IllegalStateException("Policy not initialized"));
    }
    public PolicyView current() { return PolicyView.of(currentEntity()); }

    /** Sprint 1 supports switching the existing mode for US-04; numeric policy editing belongs to Sprint 2. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PolicyView changeMode(long adminId, PolicyModeRequest request) {
        AppUser admin = users.findLockedById(adminId).orElseThrow(BusinessException::missing);
        if (admin.getRole() != AppUser.Role.ADMIN || admin.getStatus() != AppUser.Status.ACTIVE)
            throw BusinessException.forbidden("관리자만 정책을 변경할 수 있습니다.");
        PolicyVersion first = currentEntity();
        policies.findLockedById(first.getId()).orElseThrow(BusinessException::missing);
        PolicyVersion current = currentEntity();
        if (current.getVersionNo() != request.versionNo())
            throw BusinessException.conflict("POLICY_CHANGED", "다른 관리자가 정책을 변경했습니다. 최신 정책을 다시 확인해 주세요.");
        if (current.getMode() == request.mode()) return PolicyView.of(current);
        return PolicyView.of(policies.saveAndFlush(current.nextMode(request.mode(), clock.instant())));
    }
}

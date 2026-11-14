package kr.ac.cnu.lsrs.repository;
import kr.ac.cnu.lsrs.domain.PolicyVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    Optional<PolicyVersion> findFirstByOrderByVersionNoDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PolicyVersion p where p.id = :id")
    Optional<PolicyVersion> findLockedById(@Param("id") long id);
}

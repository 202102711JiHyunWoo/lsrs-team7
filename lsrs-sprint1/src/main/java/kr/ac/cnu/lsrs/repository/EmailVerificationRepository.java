package kr.ac.cnu.lsrs.repository;
import kr.ac.cnu.lsrs.domain.EmailVerification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<EmailVerification> findByTokenHash(String tokenHash);
    @Query("select v.user.id from EmailVerification v where v.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.tokenHash = :hash")
    Optional<EmailVerification> findLockedByTokenHash(@Param("hash") String hash);
    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(long userId);
    @Modifying
    @Query("update EmailVerification v set v.consumedAt = :now where v.user.id = :userId and v.consumedAt is null")
    int invalidateUnused(@Param("userId") long userId, @Param("now") java.time.Instant now);
}

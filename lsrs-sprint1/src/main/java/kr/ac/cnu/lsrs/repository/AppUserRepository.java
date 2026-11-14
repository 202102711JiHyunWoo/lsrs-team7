package kr.ac.cnu.lsrs.repository;
import kr.ac.cnu.lsrs.domain.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByStudentNo(String studentNo);
    boolean existsByEmail(String email);
    boolean existsByStudentNo(String studentNo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findLockedById(@Param("id") long id);
}

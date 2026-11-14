package kr.ac.cnu.lsrs.repository;
import kr.ac.cnu.lsrs.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllByOrderByCodeAsc();
    Optional<Seat> findByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findLockedById(@Param("id") long id);
}

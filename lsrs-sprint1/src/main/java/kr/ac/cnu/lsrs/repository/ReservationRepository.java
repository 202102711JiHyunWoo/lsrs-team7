package kr.ac.cnu.lsrs.repository;
import kr.ac.cnu.lsrs.domain.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    interface OwnerReference { Long getUserId(); Long getSeatId(); }
    @Query("select r.user.id as userId, r.seat.id as seatId from Reservation r where r.id = :id")
    Optional<OwnerReference> ownerReference(@Param("id") long id);
    @EntityGraph(attributePaths = {"seat", "user", "policy"})
    List<Reservation> findByUserIdOrderByStartAtDescIdDesc(long userId);

    @EntityGraph(attributePaths = {"seat", "user", "policy"})
    @Query("select r from Reservation r where r.status in :states and r.startAt < :end and r.endAt > :start")
    List<Reservation> findOverlapping(@Param("start") Instant start, @Param("end") Instant end,
                                     @Param("states") Collection<Reservation.Status> states);

    @Query("select count(r) from Reservation r where r.status in :states and r.startAt < :end and r.endAt > :start and (r.user.id = :userId or r.seat.id = :seatId)")
    long countConflicts(@Param("userId") long userId, @Param("seatId") long seatId,
                       @Param("start") Instant start, @Param("end") Instant end,
                       @Param("states") Collection<Reservation.Status> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findLockedById(@Param("id") long id);
}

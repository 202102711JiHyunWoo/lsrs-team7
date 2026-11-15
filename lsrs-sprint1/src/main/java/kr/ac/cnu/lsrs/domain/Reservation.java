package kr.ac.cnu.lsrs.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "reservation")
public class Reservation {
    public enum Status { RESERVED, CHECKED_IN, COMPLETED, AUTO_CANCELLED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "seat_id") private Seat seat;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "policy_version_id") private PolicyVersion policy;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    @Column(name = "reserved_at", nullable = false) private Instant reservedAt;
    @Column(name = "check_in_deadline_at", nullable = false) private Instant checkInDeadlineAt;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    protected Reservation() { }
    public Reservation(AppUser user, Seat seat, PolicyVersion policy, Instant startAt, Instant endAt, Instant now) {
        this.user = user; this.seat = seat; this.policy = policy; this.startAt = startAt; this.endAt = endAt;
        reservedAt = now; checkInDeadlineAt = startAt.plusSeconds(policy.getGraceMinutes() * 60L);
        status = Status.RESERVED;
    }
    public void checkIn(Instant now) { status = Status.CHECKED_IN; checkedInAt = now; }
    public void complete(Instant now) { status = Status.COMPLETED; completedAt = now; }
    public void cancel() { status = Status.CANCELLED; }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Seat getSeat() { return seat; }
    public PolicyVersion getPolicy() { return policy; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Instant getReservedAt() { return reservedAt; }
    public Instant getCheckInDeadlineAt() { return checkInDeadlineAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Status getStatus() { return status; }
}

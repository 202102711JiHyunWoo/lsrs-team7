package kr.ac.cnu.lsrs.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "policy_version")
public class PolicyVersion {
    public enum Mode { NORMAL, EXAM }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "version_no", nullable = false, unique = true) private long versionNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Mode mode;
    @Column(name = "normal_max_minutes", nullable = false) private int normalMaxMinutes;
    @Column(name = "exam_max_minutes", nullable = false) private int examMaxMinutes;
    @Column(name = "grace_minutes", nullable = false) private int graceMinutes;
    @Column(name = "reservation_unit_minutes", nullable = false) private int reservationUnitMinutes;
    @Column(name = "no_show_threshold", nullable = false) private int noShowThreshold;
    @Column(name = "restriction_days", nullable = false) private int restrictionDays;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected PolicyVersion() { }
    public PolicyVersion(long versionNo, Mode mode, Instant now) {
        this.versionNo = versionNo; this.mode = mode; this.createdAt = now;
        normalMaxMinutes = 240; examMaxMinutes = 180; graceMinutes = 15;
        reservationUnitMinutes = 30; noShowThreshold = 3; restrictionDays = 3;
    }
    public PolicyVersion nextMode(Mode newMode, Instant now) {
        PolicyVersion next = new PolicyVersion(versionNo + 1, newMode, now);
        next.normalMaxMinutes = normalMaxMinutes; next.examMaxMinutes = examMaxMinutes;
        next.graceMinutes = graceMinutes; next.reservationUnitMinutes = reservationUnitMinutes;
        next.noShowThreshold = noShowThreshold; next.restrictionDays = restrictionDays;
        return next;
    }
    public Long getId() { return id; }
    public long getVersionNo() { return versionNo; }
    public Mode getMode() { return mode; }
    public int getMaxMinutes() { return mode == Mode.EXAM ? examMaxMinutes : normalMaxMinutes; }
    public int getNormalMaxMinutes() { return normalMaxMinutes; }
    public int getExamMaxMinutes() { return examMaxMinutes; }
    public int getGraceMinutes() { return graceMinutes; }
    public int getReservationUnitMinutes() { return reservationUnitMinutes; }
    public int getNoShowThreshold() { return noShowThreshold; }
    public int getRestrictionDays() { return restrictionDays; }
}

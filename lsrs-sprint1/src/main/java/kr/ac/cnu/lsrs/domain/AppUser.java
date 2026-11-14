package kr.ac.cnu.lsrs.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    public enum Role { STUDENT, ADMIN }
    public enum Status { PENDING, ACTIVE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_no", length = 12, unique = true)
    private String studentNo;
    @Column(nullable = false, length = 254, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "no_show_count", nullable = false)
    private int noShowCount;
    @Column(name = "restricted_until")
    private Instant restrictedUntil;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() { }
    public AppUser(String studentNo, String email, String passwordHash, Role role, Instant now) {
        this.studentNo = studentNo; this.email = email; this.passwordHash = passwordHash;
        this.role = role; this.status = Status.PENDING; this.createdAt = now;
    }
    public void activate() { status = Status.ACTIVE; }
    public void restrictUntil(Instant until) { restrictedUntil = until; }
    public boolean isRestricted(Instant now) { return restrictedUntil != null && now.isBefore(restrictedUntil); }
    public Long getId() { return id; }
    public String getStudentNo() { return studentNo; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public int getNoShowCount() { return noShowCount; }
    public Instant getRestrictedUntil() { return restrictedUntil; }
}

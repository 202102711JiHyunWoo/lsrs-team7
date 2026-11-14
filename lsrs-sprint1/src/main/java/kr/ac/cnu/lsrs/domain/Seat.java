package kr.ac.cnu.lsrs.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "seat")
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 8) private String code;
    @Column(nullable = false, length = 24) private String zone;
    @Column(name = "qr_token_hash", nullable = false, unique = true, length = 64) private String qrTokenHash;
    protected Seat() { }
    public Seat(String code, String zone, String qrTokenHash) {
        this.code = code; this.zone = zone; this.qrTokenHash = qrTokenHash;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getZone() { return zone; }
    public String getQrTokenHash() { return qrTokenHash; }
}

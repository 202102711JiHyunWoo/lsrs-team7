package kr.ac.cnu.lsrs.config;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Component
public class DataInitializer implements ApplicationRunner {
    private final AppUserRepository users; private final SeatRepository seats;
    private final PolicyVersionRepository policies; private final ReservationRepository reservations;
    private final QrService qr; private final PasswordEncoder passwords; private final Clock clock;
    private final boolean demo; private final PolicyVersion.Mode mode;
    public DataInitializer(AppUserRepository users, SeatRepository seats, PolicyVersionRepository policies,
                           ReservationRepository reservations, QrService qr, PasswordEncoder passwords, Clock clock,
                           @Value("${app.demo:false}") boolean demo, @Value("${app.initial-policy-mode}") PolicyVersion.Mode mode) {
        this.users = users; this.seats = seats; this.policies = policies; this.reservations = reservations;
        this.qr = qr; this.passwords = passwords; this.clock = clock; this.demo = demo; this.mode = mode;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (policies.count() == 0) policies.saveAndFlush(new PolicyVersion(1, mode, clock.instant()));
        if (seats.count() == 0) {
            for (char row = 'A'; row <= 'H'; row++) for (int column = 1; column <= 6; column++) {
                String code = "%c%02d".formatted(row, column);
                seats.save(new Seat(code, row <= 'D' ? "QUIET" : "OPEN", TokenHash.sha256(qr.token(code))));
            }
        } else if (seats.findAllByOrderByCodeAsc().stream().anyMatch(seat -> !TokenHash.matches(qr.token(seat.getCode()), seat.getQrTokenHash()))) {
            throw new IllegalStateException("Saved seat QR codes require the original data/jwt.key or LSRS_JWT_SECRET.");
        }
        if (demo) initializeDemo();
    }
    private void initializeDemo() {
        demoUser("20260001", "student@example.test", AppUser.Role.STUDENT);
        demoUser(null, "admin@example.test", AppUser.Role.ADMIN);
        AppUser other = demoUser("20260002", "other@example.test", AppUser.Role.STUDENT);
        AppUser waiting = demoUser("20260003", "reserved@example.test", AppUser.Role.STUDENT);
        if (reservations.count() == 0) {
            PolicyVersion policy = policies.findFirstByOrderByVersionNoDesc().orElseThrow();
            Instant start = clock.instant().atZone(TimeRules.ZONE).withHour(10).withMinute(0).withSecond(0).withNano(0).toInstant();
            Reservation inUse = new Reservation(other, seats.findByCode("A02").orElseThrow(), policy, start, start.plusSeconds(7200), start);
            inUse.checkIn(start); reservations.save(inUse);
            reservations.save(new Reservation(waiting, seats.findByCode("A05").orElseThrow(), policy, start, start.plusSeconds(3600), start));
        }
    }
    private AppUser demoUser(String studentNo, String email, AppUser.Role role) {
        return users.findByEmail(email).orElseGet(() -> {
            AppUser user = new AppUser(studentNo, email, passwords.encode("Demo1234!"), role, clock.instant()); user.activate();
            return users.saveAndFlush(user);
        });
    }
}

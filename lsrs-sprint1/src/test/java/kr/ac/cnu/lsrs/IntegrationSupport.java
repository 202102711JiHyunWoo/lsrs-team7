package kr.ac.cnu.lsrs;

import com.jayway.jsonpath.JsonPath;
import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.service.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationSupport.TimeConfiguration.class)
public abstract class IntegrationSupport {
    public static final String PASSWORD = "Example123!";
    private static final String HASH = new BCryptPasswordEncoder(11).encode(PASSWORD);
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected AppUserRepository users;
    @Autowired protected SeatRepository seats;
    @Autowired protected ReservationRepository reservations;
    @Autowired protected PolicyVersionRepository policyRows;
    @Autowired protected EmailVerificationRepository verifications;
    @Autowired protected AuthService auth;
    @Autowired protected ReservationService reservationService;
    @Autowired protected CheckInService checkInService;
    @Autowired protected QrService qr;
    @Autowired protected PolicyService policies;
    @Autowired protected MutableTestClock clock;
    @Value("${app.mail.outbox-directory}") protected String mailboxDirectory;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary MutableTestClock testClock() { return new MutableTestClock(); } }

    @BeforeEach
    void resetDatabaseAndClock() throws Exception {
        clock.set(Instant.parse("2026-11-14T01:00:00Z"));
        reservations.deleteAllInBatch(); verifications.deleteAllInBatch(); users.deleteAllInBatch(); policyRows.deleteAllInBatch();
        policyRows.saveAndFlush(new PolicyVersion(1, PolicyVersion.Mode.NORMAL, clock.instant()));
        Path directory = Path.of(mailboxDirectory);
        if (Files.exists(directory)) try (var paths = Files.list(directory)) { for (Path path : paths.toList()) Files.delete(path); }
    }
    protected AppUser user(String studentNo, String email) { return user(studentNo, email, AppUser.Role.STUDENT); }
    protected AppUser user(String studentNo, String email, AppUser.Role role) {
        AppUser user = new AppUser(studentNo, email, HASH, role, clock.instant()); user.activate(); return users.saveAndFlush(user);
    }
    protected AppUser firstUser() { return user("20260001", "first@example.test"); }
    protected AppUser secondUser() { return user("20260002", "second@example.test"); }
    protected AppUser admin() { return user(null, "admin@example.test", AppUser.Role.ADMIN); }
    protected Seat seat(int index) { return seats.findAllByOrderByCodeAsc().get(index); }
    protected String token(AppUser user) { return auth.login(new LoginRequest(user.getEmail(), PASSWORD)).accessToken(); }
    protected ReservationRequest request(Seat seat, String start, String end) {
        return new ReservationRequest(seat.getId(), OffsetDateTime.parse(start), OffsetDateTime.parse(end), policies.current().versionNo());
    }
    protected ReservationRequest futureRequest(Seat seat) { return request(seat, "2026-11-14T10:30:00+09:00", "2026-11-14T11:30:00+09:00"); }
    protected ReservationRequest currentRequest(Seat seat) { return request(seat, "2026-11-14T10:00:00+09:00", "2026-11-14T11:00:00+09:00"); }
    protected ResultActions send(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType("application/json").content(json.writeValueAsString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return mvc.perform(builder);
    }
    protected ResultActions read(String path, String token) throws Exception {
        MockHttpServletRequestBuilder builder = get(path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return mvc.perform(builder);
    }
    protected String content(ResultActions result) throws Exception { return result.andReturn().getResponse().getContentAsString(); }
    protected String value(String body, String path) { return JsonPath.read(body, path).toString(); }
    protected Map<String,Object> signup(String email, String studentNo) {
        return Map.of("email", email, "studentNo", studentNo, "password", PASSWORD, "requestedRole", "STUDENT");
    }
    protected String latestMailToken(String email) throws Exception {
        List<Properties> matches = new ArrayList<>();
        try (var paths = Files.list(Path.of(mailboxDirectory))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".properties")).toList()) {
                Properties properties = new Properties(); try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
                if (properties.getProperty("to").equalsIgnoreCase(email)) matches.add(properties);
            }
        }
        matches.sort(Comparator.comparing(properties -> properties.getProperty("sentAt")));
        String link = matches.getLast().getProperty("link"); return link.substring(link.indexOf("token=") + 6);
    }
}

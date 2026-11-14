package kr.ac.cnu.lsrs.service;

import kr.ac.cnu.lsrs.domain.*;
import kr.ac.cnu.lsrs.repository.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    public record PendingMail(UserView user, String token) { }
    private final AppUserRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final Set<String> adminEmails;
    private final long verificationMinutes;
    private final long jwtMinutes;
    private final String issuer;
    public AuthService(AppUserRepository users, EmailVerificationRepository verifications, PasswordEncoder passwords,
                       JwtEncoder encoder, Clock clock, @Value("${app.admin-emails:}") String adminEmails,
                       @Value("${app.mail.verification-minutes}") long verificationMinutes,
                       @Value("${app.jwt.lifetime-minutes}") long jwtMinutes, @Value("${app.jwt.issuer}") String issuer) {
        this.users = users; this.verifications = verifications; this.passwords = passwords; this.encoder = encoder; this.clock = clock;
        this.adminEmails = new HashSet<>();
        for (String email : adminEmails.split(",")) if (!email.isBlank()) this.adminEmails.add(normalize(email));
        this.verificationMinutes = verificationMinutes; this.jwtMinutes = jwtMinutes; this.issuer = issuer;
    }
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    @Transactional
    public PendingMail register(RegisterRequest request) {
        String email = normalize(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72 ||
                !request.password().matches("(?s)^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])[^\\s]{8,72}$"))
            throw BusinessException.bad("비밀번호는 영문·숫자·특수문자를 포함해 8자 이상, UTF-8 72바이트 이내로 입력해 주세요.");
        AppUser.Role role = "ADMIN".equals(request.requestedRole()) ? AppUser.Role.ADMIN : AppUser.Role.STUDENT;
        if (role == AppUser.Role.ADMIN && !adminEmails.contains(email))
            throw BusinessException.forbidden("관리자로 사전 등록된 이메일만 관리자 가입을 할 수 있습니다.");
        String studentNo = request.studentNo() == null || request.studentNo().isBlank() ? null : request.studentNo().strip();
        if ((role == AppUser.Role.STUDENT && studentNo == null) || (studentNo != null && !studentNo.matches("[0-9]{8,12}")))
            throw BusinessException.bad("학생 학번을 숫자 8~12자리로 입력해 주세요.");
        if (users.existsByEmail(email) || (studentNo != null && users.existsByStudentNo(studentNo)))
            throw BusinessException.conflict("DUPLICATE_ACCOUNT", "이미 등록된 이메일 또는 학번입니다.");
        AppUser user = users.saveAndFlush(new AppUser(studentNo, email, passwords.encode(request.password()), role, clock.instant()));
        return issue(user);
    }
    private PendingMail issue(AppUser user) {
        String token = TokenHash.random(); Instant now = clock.instant();
        verifications.save(new EmailVerification(user, TokenHash.sha256(token), now, now.plusSeconds(verificationMinutes * 60)));
        return new PendingMail(UserView.of(user), token);
    }
    @Transactional
    public PendingMail resend(String rawEmail) {
        AppUser candidate = users.findByEmail(normalize(rawEmail)).orElse(null);
        if (candidate == null) return null;
        AppUser user = users.findLockedById(candidate.getId()).orElseThrow(BusinessException::missing);
        if (user.getStatus() == AppUser.Status.ACTIVE) return null;
        var latest = verifications.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && clock.instant().isBefore(latest.get().getCreatedAt().plusSeconds(60)))
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON", "인증 메일은 1분 후 다시 요청할 수 있습니다.");
        verifications.invalidateUnused(user.getId(), clock.instant());
        return issue(user);
    }
    @Transactional
    public UserView verify(String token) {
        String hash = TokenHash.sha256(token);
        long userId = verifications.findUserIdByTokenHash(hash).orElseThrow(() -> BusinessException.bad("유효하지 않은 이메일 인증 링크입니다."));
        AppUser user = users.findLockedById(userId).orElseThrow(BusinessException::missing);
        EmailVerification verification = verifications.findLockedByTokenHash(hash).orElseThrow(BusinessException::missing);
        Instant now = clock.instant();
        if (verification.getConsumedAt() != null)
            throw BusinessException.conflict("VERIFICATION_USED", "이미 사용한 인증 링크입니다. 인증이 완료되었다면 로그인해 주세요.");
        if (!now.isBefore(verification.getExpiresAt()))
            throw BusinessException.conflict("VERIFICATION_EXPIRED", "이메일 인증 링크가 만료되었습니다. 인증 메일을 다시 요청해 주세요.");
        verification.consume(now); user.activate();
        return UserView.of(user);
    }
    @Transactional(readOnly = true)
    public LoginView login(LoginRequest request) {
        String identifier = normalize(request.identifier());
        AppUser user = (identifier.contains("@") ? users.findByEmail(identifier) : users.findByStudentNo(identifier)).orElse(null);
        boolean valid = user != null && request.password().getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(request.password(), user.getPasswordHash());
        if (!valid) throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "학번·이메일 또는 비밀번호가 올바르지 않습니다.");
        if (user.getStatus() != AppUser.Status.ACTIVE)
            throw new BusinessException(HttpStatus.FORBIDDEN, "EMAIL_UNVERIFIED", "이메일 인증을 완료한 후 로그인해 주세요.");
        Instant now = clock.instant(), expiry = now.plusSeconds(jwtMinutes * 60);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString()).issuedAt(now)
                .expiresAt(expiry).claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginView(token, "Bearer", expiry, UserView.of(user));
    }
    @Transactional(readOnly = true)
    public UserView me(long userId) { return UserView.of(users.findById(userId).orElseThrow(BusinessException::missing)); }
}

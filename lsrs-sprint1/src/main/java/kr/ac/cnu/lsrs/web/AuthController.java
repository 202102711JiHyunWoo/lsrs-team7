package kr.ac.cnu.lsrs.web;

import jakarta.validation.Valid;
import kr.ac.cnu.lsrs.config.CurrentUser;
import kr.ac.cnu.lsrs.service.*;
import kr.ac.cnu.lsrs.web.ApiDtos.*;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);
    private final AuthService auth;
    private final MailGateway mail;
    public AuthController(AuthService auth, MailGateway mail) { this.auth = auth; this.mail = mail; }
    @PostMapping("/users")
    public ResponseEntity<RegistrationView> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.PendingMail pending = auth.register(request);
        String delivery = deliver(pending);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationView(pending.user(), delivery,
                delivery.equals("FAILED") ? "계정이 생성되었으나 메일 발송에 실패했습니다. 잠시 후 인증 메일을 다시 요청해 주세요." :
                        delivery.equals("LOCAL_MAILBOX") ? "계정이 생성되었습니다. 개발용 메일함에서 이메일 인증을 완료해 주세요." : "인증 메일을 보냈습니다. 메일함을 확인해 주세요."));
    }
    @PostMapping("/email-verifications")
    public UserView verify(@Valid @RequestBody VerificationRequest request) { return auth.verify(request.token()); }
    @PostMapping("/email-verifications/resend")
    public MessageView resend(@Valid @RequestBody ResendRequest request) {
        AuthService.PendingMail pending = auth.resend(request.email());
        if (pending != null && deliver(pending).equals("FAILED"))
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        return new MessageView("인증 대기 중인 계정이면 인증 메일을 발송했습니다. 메일함을 확인해 주세요.");
    }
    private String deliver(AuthService.PendingMail pending) {
        try { return mail.sendVerification(pending.user().email(), pending.token()); }
        catch (RuntimeException exception) {
            LOG.warn("Verification email delivery failed: {}", exception.getClass().getSimpleName());
            return "FAILED";
        }
    }
    @PostMapping("/auth/tokens")
    public LoginView login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal CurrentUser user) { return auth.me(user.id()); }
}

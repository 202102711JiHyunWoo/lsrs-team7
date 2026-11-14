package kr.ac.cnu.lsrs.web;
import kr.ac.cnu.lsrs.web.ApiDtos.AppInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@Controller
public class AppController {
    private final Clock clock; private final boolean demo, localMailbox; private final int pollingSeconds;
    public AppController(Clock clock, @Value("${app.demo:false}") boolean demo,
                         @Value("${app.mail.mode}") String mailMode, @Value("${app.polling-seconds}") int pollingSeconds,
                         Environment environment) {
        this.clock = clock; this.demo = demo; this.pollingSeconds = pollingSeconds;
        localMailbox = mailMode.equals("file") && environment.acceptsProfiles(Profiles.of("local", "demo"));
    }
    @GetMapping({"/", "/admin"}) public String home() { return "index"; }
    @GetMapping("/verify-email") public String verify() { return "verify-email"; }
    @GetMapping("/api/app-info") @ResponseBody
    public AppInfo info() { return new AppInfo(clock.instant(), "Asia/Seoul", demo, localMailbox, pollingSeconds); }
}

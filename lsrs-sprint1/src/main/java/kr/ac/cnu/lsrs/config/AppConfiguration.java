package kr.ac.cnu.lsrs.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;

@Configuration
public class AppConfiguration {
    @Bean Clock appClock(@Value("${app.fixed-time:}") String fixedTime,
                         @Value("${app.demo:false}") boolean demo) {
        if (demo && !fixedTime.isBlank()) return Clock.fixed(OffsetDateTime.parse(fixedTime).toInstant(), ZoneOffset.UTC);
        return Clock.systemUTC();
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(11); }
}

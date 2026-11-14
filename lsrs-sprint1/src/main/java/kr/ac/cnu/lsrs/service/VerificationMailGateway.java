package kr.ac.cnu.lsrs.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Component
public class VerificationMailGateway implements MailGateway {
    private final String mode, baseUrl, from;
    private final Path directory;
    private final JavaMailSender mail;
    private final Clock clock;
    private final int verificationMinutes;
    public VerificationMailGateway(@Value("${app.mail.mode}") String mode, @Value("${app.public-base-url}") String baseUrl,
                                   @Value("${app.mail.from}") String from, @Value("${app.mail.outbox-directory}") String directory,
                                   @Value("${app.mail.verification-minutes}") int verificationMinutes,
                                   JavaMailSender mail, Clock clock, Environment environment) {
        this.verificationMinutes = verificationMinutes;
        this.mode = mode; this.baseUrl = baseUrl.replaceAll("/$", ""); this.from = from;
        this.directory = Path.of(directory); this.mail = mail; this.clock = clock;
        if (mode.equals("file") && !environment.acceptsProfiles(Profiles.of("local", "demo", "test")))
            throw new IllegalStateException("File mailbox is only available in local/demo/test profiles.");
        if (!Set.of("smtp", "file").contains(mode)) throw new IllegalStateException("Unknown mail mode: " + mode);
    }
    @Override public String sendVerification(String email, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        if (mode.equals("smtp")) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(email); message.setSubject("[LSRS] 이메일 인증을 완료해 주세요");
            message.setText("도서관 좌석 예약 시스템\n\n아래 링크에서 이메일 인증을 완료해 주세요.\n" + link + "\n\n인증 링크는 " + verificationMinutes + "분 동안 유효합니다.");
            mail.send(message); return "SMTP_SENT";
        }
        try {
            Files.createDirectories(directory);
            Properties properties = new Properties();
            properties.setProperty("id", UUID.randomUUID().toString()); properties.setProperty("to", email);
            properties.setProperty("subject", "[LSRS] 이메일 인증을 완료해 주세요");
            properties.setProperty("link", link); properties.setProperty("sentAt", clock.instant().toString());
            Path temporary = Files.createTempFile(directory, "mail-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "LSRS local verification mail"); }
            Files.move(temporary, directory.resolve(properties.getProperty("id") + ".properties"), StandardCopyOption.REPLACE_EXISTING);
            return "LOCAL_MAILBOX";
        } catch (IOException exception) { throw new IllegalStateException("Could not save local verification mail", exception); }
    }
}

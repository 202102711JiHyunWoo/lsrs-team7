package kr.ac.cnu.lsrs.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.ac.cnu.lsrs.service.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

@Controller
@Profile({"local", "demo"})
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "file")
public class LocalMailboxController {
    private final Path directory;
    public LocalMailboxController(@Value("${app.mail.outbox-directory}") String directory) { this.directory = Path.of(directory); }
    private void requireLoopback(HttpServletRequest request) {
        try { if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) throw BusinessException.forbidden("개발용 메일함은 이 컴퓨터에서만 열 수 있습니다."); }
        catch (UnknownHostException exception) { throw BusinessException.forbidden("개발용 메일함에 접근할 수 없습니다."); }
    }
    @GetMapping("/dev/mailbox") public String page(HttpServletRequest request) { requireLoopback(request); return "mailbox"; }
    @GetMapping("/api/dev/mailbox") @ResponseBody
    public List<Map<String, String>> messages(HttpServletRequest request, @RequestParam(required = false) String email) throws IOException {
        requireLoopback(request);
        if (!Files.exists(directory)) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".properties")).toList()) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
                if (email != null && !properties.getProperty("to", "").equalsIgnoreCase(email.strip())) continue;
                Map<String, String> row = new HashMap<>();
                for (String key : List.of("id", "to", "subject", "link", "sentAt")) row.put(key, properties.getProperty(key, ""));
                result.add(row);
            }
        }
        result.sort(Comparator.comparing((Map<String, String> row) -> row.get("sentAt")).reversed());
        return result;
    }
}

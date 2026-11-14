package kr.ac.cnu.lsrs.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import kr.ac.cnu.lsrs.service.TokenHash;

@Component
public class KeyMaterial {
    private final byte[] secret;
    public KeyMaterial(@Value("${app.jwt.secret:}") String configured,
                       @Value("${app.jwt.key-file}") String keyFile, Environment environment) throws IOException {
        String value = configured;
        if (value.isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("local", "demo")))
                throw new IllegalStateException("LSRS_JWT_SECRET must be configured outside local/demo profiles.");
            Path path = Path.of(keyFile).toAbsolutePath();
            if (Files.exists(path)) value = Files.readString(path).trim();
            else {
                Files.createDirectories(path.getParent()); value = TokenHash.random();
                Files.writeString(path, value, StandardOpenOption.CREATE_NEW);
                try { Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
                catch (UnsupportedOperationException ignored) { /* Windows uses inherited ACLs. */ }
            }
        }
        secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("JWT signing key must contain at least 32 bytes.");
    }
    public byte[] bytes() { return secret.clone(); }
}

package kr.ac.cnu.lsrs.config;
import kr.ac.cnu.lsrs.domain.AppUser;
import java.security.Principal;

public record CurrentUser(long id, String email, AppUser.Role role) implements Principal {
    @Override public String getName() { return email; }
}

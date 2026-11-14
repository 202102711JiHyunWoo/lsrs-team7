package kr.ac.cnu.lsrs.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import kr.ac.cnu.lsrs.domain.AppUser;
import kr.ac.cnu.lsrs.repository.AppUserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final AppUserRepository users;
    public JwtAuthenticationFilter(JwtDecoder decoder, AppUserRepository users) { this.decoder = decoder; this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jwt jwt = decoder.decode(header.substring(7));
                long id = Long.parseLong(jwt.getSubject());
                AppUser user = users.findById(id).orElseThrow(() -> new BadJwtException("Unknown user"));
                if (user.getStatus() != AppUser.Status.ACTIVE) throw new BadJwtException("Inactive user");
                CurrentUser principal = new CurrentUser(id, user.getEmail(), user.getRole());
                var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                SecurityConfig.securityError(response, 401, "UNAUTHENTICATED", "로그인 정보가 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

package com.museotek.box.infrastructure.security;

import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Logs in a display when a {@code /display/**} request carries its display token as a Bearer token. The principal is
 * the {@link PlayerDisplayView}: the one Box the token is tied to. Anything else stays anonymous and is refused by
 * the chain. Not a bean on purpose: as a bean, Spring Boot would also run it on every request outside that chain.
 */
public class DisplayTokenFilter extends OncePerRequestFilter {

    public static final String ROLE = "DISPLAY";

    private static final Logger log = LoggerFactory.getLogger(DisplayTokenFilter.class);
    private static final String BEARER = "Bearer ";
    // Real tokens are 43 characters; anything far longer is not worth a database lookup.
    private static final int MAX_TOKEN_LENGTH = 64;

    private final VerifyDisplayTokenUseCase verifyUseCase;

    public DisplayTokenFilter(VerifyDisplayTokenUseCase verifyUseCase) {
        this.verifyUseCase = verifyUseCase;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            if (token.length() <= MAX_TOKEN_LENGTH) {
                try {
                    PlayerDisplayView display = verifyUseCase.execute(token);
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            display, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
                    SecurityContextHolder.setContext(context);
                } catch (InvalidDisplayTokenException e) {
                    log.debug("Refused a {} {} call: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}

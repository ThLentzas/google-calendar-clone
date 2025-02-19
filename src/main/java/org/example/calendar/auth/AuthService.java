package org.example.calendar.auth;

import org.example.calendar.auth.dto.RegisterRequest;
import org.example.calendar.entity.User;
import org.example.calendar.exception.UnauthorizedException;
import org.example.calendar.auth.dto.RefreshToken;
import org.example.calendar.user.UserPrincipal;
import org.example.calendar.user.UserService;
import org.example.calendar.utils.CookieUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AuthService {
    private final JwtService jwtService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Transactional
    void registerUser(RegisterRequest registerRequest, HttpServletResponse servletResponse) {
        User user = User.builder()
                .email(registerRequest.email())
                .username(registerRequest.username())
                .password(registerRequest.password())
                .build();

        this.userService.registerUser(user);
        setSecurityContext(user);

        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String accessTokenValue = this.jwtService.generateToken(userPrincipal);
        String refreshTokenValue = this.refreshTokenService.generateToken(userPrincipal);
        // Access token duration 5 minutes, refresh token 1 week
        CookieUtils.addAuthCookies(accessTokenValue, 5, refreshTokenValue, 10800, servletResponse);
    }

    void refresh(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Cookie cookie = CookieUtils.getCookie(servletRequest, "REFRESH_TOKEN");
        if (cookie == null) {
            logger.info("Refresh token was not found");
            throw new UnauthorizedException("Unauthorized");
        }

        RefreshToken refreshToken = this.refreshTokenService.findByTokenValue(cookie.getValue());
        User user = this.userService.findById(refreshToken.getUserId());
        UserPrincipal userPrincipal = new UserPrincipal(user);
        String accessTokenValue = this.jwtService.generateToken(userPrincipal);
        String refreshTokenValue = this.refreshTokenService.generateToken(userPrincipal);
        CookieUtils.addAuthCookies(accessTokenValue, 5, refreshTokenValue, 10800, servletResponse);
        //Delete the original refresh token
        this.refreshTokenService.deleteToken(cookie.getValue());
    }

    void revoke(HttpServletResponse servletResponse) {
        CookieUtils.addAuthCookies("", 0, "", 0, servletResponse);
    }

    private void setSecurityContext(User user) {
        UserPrincipal userPrincipal = new UserPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        /*
            https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html#use-securitycontextholderstrategy

            We are not persisting the authentication like in the case of sessions. In that case, the securityContextRepository
            was an HttpSessionSecurityContextRepository, for this case is RequestAttributeSecurityContextRepository

            For more info, look at the BearerTokenAuthenticationFilter
         */
        SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        this.securityContextHolderStrategy.setContext(context);
    }
}

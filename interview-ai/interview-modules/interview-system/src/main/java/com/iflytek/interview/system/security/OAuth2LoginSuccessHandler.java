package com.iflytek.interview.system.security;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.service.AuthService;
import com.iflytek.interview.system.service.OAuthUserService;
import com.iflytek.interview.system.vo.LoginVO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component("oAuth2LoginSuccessHandler")
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String LOGIN_CODE_PREFIX = "oauth:login:";
    private static final String BIND_TICKET_PREFIX = "oauth:bind:";
    private static final String BIND_COOKIE_NAME = "OAUTH_BIND_TICKET";

    @Autowired
    private OAuthUserService oAuthUserService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.oauth.frontend-redirect:http://localhost:5173/oauth/callback}")
    private String frontendRedirect;

    @Value("${app.oauth.frontend-bind-redirect:http://localhost:5173/profile}")
    private String frontendBindRedirect;

    @Value("${app.oauth.login-code-ttl-seconds:60}")
    private long loginCodeTtlSeconds;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        Cookie bindCookie = WebUtils.getCookie(request, BIND_COOKIE_NAME);
        if (bindCookie != null) {
            handleBindSuccess(request, response, oAuth2User, bindCookie.getValue());
            return;
        }

        handleLoginSuccess(response, oAuth2User);
    }

    private void handleBindSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2User oAuth2User,
            String bindTicket) throws IOException {

        try {
            Object userIdValue = redisTemplate.opsForValue().get(BIND_TICKET_PREFIX + bindTicket);

            if (userIdValue == null) {
                response.sendRedirect(frontendBindRedirect + "?oauthBind=expired");
                return;
            }

            Long bindUserId = Long.valueOf(String.valueOf(userIdValue));
            oAuthUserService.bindExistingUser("gitee", oAuth2User, bindUserId);

            redisTemplate.delete(BIND_TICKET_PREFIX + bindTicket);
            response.sendRedirect(frontendBindRedirect + "?oauthBind=success");
        } catch (BusinessException exception) {
            response.sendRedirect(frontendBindRedirect + "?oauthBind=conflict");
        } finally {
            clearBindCookie(response);
        }
    }


    private void clearBindCookie(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie
                .from(BIND_COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    private void handleLoginSuccess(
            HttpServletResponse response,
            OAuth2User oAuth2User) throws IOException {

        User user = oAuthUserService.findOrCreate("gitee", oAuth2User);
        LoginVO loginVO = authService.issueLogin(user);

        String loginCode = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                LOGIN_CODE_PREFIX + loginCode,
                loginVO,
                Duration.ofSeconds(loginCodeTtlSeconds));

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendRedirect)
                .queryParam("loginCode", loginCode)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}

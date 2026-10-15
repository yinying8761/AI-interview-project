package com.iflytek.interview.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器：解析请求头的 Bearer Token，取出 sessionId，
 * 再从 Redis 会话还原角色/权限并写入 SecurityContext（第9讲）。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头取 token（约定带 Bearer 前缀）
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);   // 没有 token，直接放行（后续由 Security 决定 401/放行）
            return;
        }
        String token = authHeader.substring(7);

        try {
            // 2. 解析 JWT，取出会话 key
            Claims claims = jwtUtil.parseToken(token);
            String sessionId = claims.get("sessionId", String.class);

            // 3. 从 Redis 会话还原身份（角色/权限以 Redis 为准，改权限/登出即时生效）
            LoginUser session = sessionService.getSession(sessionId);
            if (session == null) {
                // token 本身有效，但会话已不存在（已登出/被删）→ 视为未认证
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            // 补上会话 key，方便控制器做登出
            session.setSessionId(sessionId);

            // 4. 组装 authorities：每个角色一个 ROLE_<r>，再加所有权限编码
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (session.getRoles() != null) {
                session.getRoles().forEach(r ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            if (session.getPermissions() != null) {
                session.getPermissions().forEach(p ->
                        authorities.add(new SimpleGrantedAuthority(p)));
            }

            // 5. 把身份封装成认证对象，写入 SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(session, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // token 无效/过期：清空上下文，后续由异常入口返回 401
            log.warn("JWT 认证失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
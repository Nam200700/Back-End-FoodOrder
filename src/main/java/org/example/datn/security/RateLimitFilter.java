package org.example.datn.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limit on {@code /api/v1/auth/**} to blunt brute-force /
 * OTP abuse. In-memory for the DATN scope; use Redis in production.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String ip = req.getRemoteAddr();
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> now - t > WINDOW_MS);
            if (timestamps.size() >= MAX_REQUESTS) {
                res.setStatus(429);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(
                        "{\"success\":false,\"errorCode\":\"TOO_MANY_REQUESTS\"," +
                                "\"message\":\"Quá nhiều yêu cầu, vui lòng thử lại sau 1 phút\"}");
                return;
            }
            timestamps.addLast(now);
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getServletPath().startsWith("/api/v1/auth/");
    }
}

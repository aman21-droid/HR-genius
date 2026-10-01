package com.hrgenius.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When the Angular build is bundled into the jar (single-service deployment), deep links such as
 * /employees/11 must serve index.html so the SPA router can take over. API, docs and real files
 * pass through untouched; without a bundled frontend this filter does nothing.
 */
@Component
public class SpaForwardFilter extends OncePerRequestFilter {

    private final boolean spaBundled = new ClassPathResource("static/index.html").exists();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (spaBundled && "GET".equals(request.getMethod()) && isClientRoute(path)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isClientRoute(String path) {
        if (path.startsWith("/api/") || path.startsWith("/v3/") || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator") || path.equals("/index.html")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");   // files (main.js, styles.css, favicon.ico) are served as-is
    }
}

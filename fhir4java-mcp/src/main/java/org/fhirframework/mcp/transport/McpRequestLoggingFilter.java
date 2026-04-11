package org.fhirframework.mcp.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Debug filter to log all incoming requests to MCP endpoints.
 * This helps diagnose connection issues with MCP clients like VS Code.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/mcp") || path.equals("/sse")) {
            String method = request.getMethod();
            String contentType = request.getContentType();
            String accept = request.getHeader("Accept");
            String origin = request.getHeader("Origin");
            String sessionId = request.getHeader("Mcp-Session-Id");

            log.info("=== MCP Request ===");
            log.info("Method: {} {}", method, path);
            log.info("Content-Type: {}", contentType);
            log.info("Accept: {}", accept);
            log.info("Origin: {}", origin);
            log.info("Mcp-Session-Id: {}", sessionId);

            // Log all headers for debugging
            if (log.isDebugEnabled()) {
                Collections.list(request.getHeaderNames()).forEach(headerName ->
                    log.debug("Header {}: {}", headerName, request.getHeader(headerName))
                );
            }
            log.info("==================");
        }

        filterChain.doFilter(request, response);
    }
}

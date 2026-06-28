package org.example.core.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.time.Duration;

/**
 * API 速率限制过滤器
 * 基于 IP 地址进行限流，默认 100 请求/分钟
 */
@Slf4j
@Component
public class RateLimitFilter implements Filter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10000)
            .build();
    @Value("${ratelimit.max-requests-per-minute:100}")
    private int maxRequestsPerMinute;


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIp(httpRequest);
        Bucket bucket = getBucket(clientIp);
        
        if (bucket.tryConsume(1)) {
            // 允许请求
            chain.doFilter(request, response);
        } else {
            // 拒绝请求
            log.warn("IP {} 超出速率限制", clientIp);
            httpResponse.setStatus(429); // Too Many Requests
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    /**
     * 获取或创建桶
     */
    private Bucket getBucket(String ip) {
        return buckets.get(ip, key -> createNewBucket());
    }

    /**
     * 创建新桶（令牌桶算法）
     */
    private Bucket createNewBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(maxRequestsPerMinute, Refill.greedy(maxRequestsPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

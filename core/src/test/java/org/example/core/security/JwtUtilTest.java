package org.example.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 设置测试用的 Secret（Base64 编码的 256-bit 密钥）
        java.lang.reflect.Field secretField = null;
        try {
            secretField = JwtUtil.class.getDeclaredField("secret");
            secretField.setAccessible(true);
            secretField.set(jwtUtil, "dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3Nlc09ubHk=");
            
            java.lang.reflect.Field expirationField = JwtUtil.class.getDeclaredField("expiration");
            expirationField.setAccessible(true);
            expirationField.setLong(jwtUtil, 3600000); // 1小时
            
            java.lang.reflect.Field refreshExpirationField = JwtUtil.class.getDeclaredField("refreshExpiration");
            refreshExpirationField.setAccessible(true);
            refreshExpirationField.setLong(jwtUtil, 7200000); // 2小时
        } catch (Exception e) {
            fail("Failed to set up JwtUtil: " + e.getMessage());
        }
    }

    @Test
    void testGenerateAndValidateToken() {
        String username = "testUser";
        String token = jwtUtil.generateToken(username);
        
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals(username, jwtUtil.extractUsername(token));
    }

    @Test
    void testGenerateAndValidateRefreshToken() {
        String username = "testUser";
        String refreshToken = jwtUtil.generateRefreshToken(username);
        
        assertNotNull(refreshToken);
        assertTrue(jwtUtil.validateToken(refreshToken));
        assertEquals(username, jwtUtil.extractUsername(refreshToken));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    void testExtractUsernameFromInvalidToken() {
        assertThrows(Exception.class, () -> {
            jwtUtil.extractUsername("invalid.token.here");
        });
    }
}

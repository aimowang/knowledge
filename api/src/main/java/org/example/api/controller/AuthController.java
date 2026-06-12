package org.example.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.core.repository.RefreshTokenRepository;
import org.example.core.repository.UserRepository;
import org.example.core.security.JwtUtil;
import org.example.model.dto.AuthResponse;
import org.example.model.dto.LoginRequest;
import org.example.model.dto.RefreshRequest;
import org.example.model.entity.RefreshTokenEntity;
import org.example.model.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(UserRepository userRepository, 
                         PasswordEncoder passwordEncoder,
                         JwtUtil jwtUtil,
                         RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody LoginRequest request) {
        log.info("用户注册请求: {}", request.getUsername());

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(null, null, "用户名已存在"));
        }

        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.info("用户注册成功: {}", request.getUsername());

        return ResponseEntity.ok(new AuthResponse(null, request.getUsername(), "注册成功"));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("用户登录请求: {}", request.getUsername());

        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(new AuthResponse(null, null, "用户名或密码错误"));
        }

        // 生成 Access Token 和 Refresh Token
        String accessToken = jwtUtil.generateToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        
        // 保存 Refresh Token 到数据库
        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setToken(refreshToken);
        refreshTokenEntity.setUserId(user.getUsername());
        refreshTokenEntity.setExpiryDate(LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(refreshTokenEntity);
        
        log.info("用户登录成功: {}", request.getUsername());

        // 返回 Access Token，Refresh Token 放在响应头或单独字段
        AuthResponse response = new AuthResponse(accessToken, user.getUsername(), "登录成功");
        return ResponseEntity.ok()
                .header("X-Refresh-Token", refreshToken)
                .body(response);
    }

    /**
     * 验证 Token（可选）
     */
    @GetMapping("/verify")
    public ResponseEntity<Boolean> verifyToken(@RequestHeader("Authorization") String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        boolean valid = jwtUtil.validateToken(token);
        return ResponseEntity.ok(valid);
    }
    
    /**
     * 刷新 Access Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        log.info("收到 Refresh Token 请求");
        
        // 1. 验证 Refresh Token 格式
        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(401)
                    .body(new AuthResponse(null, null, "无效的 Refresh Token"));
        }
        
        // 2. 从数据库查询 Refresh Token
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken)
                .orElse(null);
        
        if (tokenEntity == null || tokenEntity.isRevoked()) {
            return ResponseEntity.status(401)
                    .body(new AuthResponse(null, null, "Refresh Token 已失效"));
        }
        
        // 3. 检查是否过期
        if (tokenEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            // 删除过期的 Token
            refreshTokenRepository.delete(tokenEntity);
            return ResponseEntity.status(401)
                    .body(new AuthResponse(null, null, "Refresh Token 已过期"));
        }
        
        // 4. 生成新的 Access Token
        String newAccessToken = jwtUtil.generateToken(tokenEntity.getUserId());
        
        log.info("Token 刷新成功: {}", tokenEntity.getUserId());
        return ResponseEntity.ok(new AuthResponse(newAccessToken, tokenEntity.getUserId(), "Token 刷新成功"));
    }
}

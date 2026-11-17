package com.scf.meta.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 基于内存令牌的最小认证服务（隔离开发环境演示用途）。 */
@Service
public class TokenService {

    public record Session(String username, String role, Instant expiresAt) {}

    private final Map<String, Session> tokens = new ConcurrentHashMap<>();
    private final Map<String, String[]> users = new ConcurrentHashMap<>();

    public TokenService(@Value("${scf.users.admin}") String admin,
                        @Value("${scf.users.viewer}") String viewer) {
        register(users, "admin", admin);
        register(users, "viewer", viewer);
    }

    private static void register(Map<String, String[]> target, String username, String spec) {
        // spec 格式：<密码>:<角色>
        String[] parts = spec.split(":");
        target.put(username, new String[]{parts[0], parts[1]});
    }

    public String login(String username, String password) {
        String[] u = users.get(username);
        if (u == null || !u[0].equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, new Session(username, u[1], Instant.now().plusSeconds(12 * 3600)));
        return token;
    }

    public Session authenticate(String token) {
        if (token == null) return null;
        Session s = tokens.get(token);
        if (s == null || s.expiresAt().isBefore(Instant.now())) return null;
        return s;
    }

    public String roleOf(String username) {
        String[] u = users.get(username);
        return u == null ? null : u[1];
    }
}

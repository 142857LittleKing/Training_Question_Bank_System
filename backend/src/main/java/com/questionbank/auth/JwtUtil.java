package com.questionbank.auth;

import com.questionbank.common.BizException;
import com.questionbank.common.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:72}") long expireHours) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度必须 >= 32 字符, 请通过环境变量 JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String createToken(CurrentUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .claim("role", user.role().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 token, 失败抛 401 */
    public CurrentUser parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            long id = Long.parseLong(c.getSubject());
            String username = c.get("username", String.class);
            String displayName = c.get("displayName", String.class);
            UserRole role = UserRole.valueOf(c.get("role", String.class));
            return new CurrentUser(id, username, displayName, role);
        } catch (Exception e) {
            throw BizException.unauthorized("登录凭证无效或已过期");
        }
    }
}

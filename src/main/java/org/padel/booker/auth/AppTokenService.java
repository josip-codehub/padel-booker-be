package org.padel.booker.auth;

import org.padel.booker.config.AppAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and verifies stateless HMAC-signed session tokens for the app login.
 * Token format: base64url(payload) + "." + base64url(hmacSha256(payload)), payload = "username|expiryMillis".
 */
@Service
public class AppTokenService {

    private static final Logger log = LoggerFactory.getLogger(AppTokenService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;

    public AppTokenService(AppAuthProperties props) {
        String s = props.getSecret();
        if (s == null || s.isBlank()) {
            byte[] rnd = new byte[32];
            new SecureRandom().nextBytes(rnd);
            s = Base64.getEncoder().encodeToString(rnd);
            log.warn("app.auth.secret not set — generated an ephemeral secret; issued tokens will be invalidated on restart. Set APP_AUTH_SECRET to persist logins.");
        }
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    public String create(String username, long ttlMillis) {
        long expiry = System.currentTimeMillis() + ttlMillis;
        String payload = B64.encodeToString((username + "|" + expiry).getBytes(StandardCharsets.UTF_8));
        return payload + "." + B64.encodeToString(hmac(payload));
    }

    public Optional<String> verify(String token) {
        if (token == null) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0) return Optional.empty();
        String payload = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        try {
            if (!MessageDigest.isEqual(B64D.decode(sig), hmac(payload))) return Optional.empty();
            String decoded = new String(B64D.decode(payload), StandardCharsets.UTF_8);
            int bar = decoded.lastIndexOf('|');
            if (bar <= 0) return Optional.empty();
            long expiry = Long.parseLong(decoded.substring(bar + 1));
            if (System.currentTimeMillis() > expiry) return Optional.empty();
            return Optional.of(decoded.substring(0, bar));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}

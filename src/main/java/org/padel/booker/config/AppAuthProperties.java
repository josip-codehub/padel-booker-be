package org.padel.booker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** App-level login (separate from the Sporty Plus credentials) that gates the API. */
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    /** Username for the app login. */
    private String username = "admin";
    /** Password for the app login. If blank, the API is locked down (fail closed). */
    private String password;
    /** HMAC secret for signing session tokens. If blank, an ephemeral one is generated at boot. */
    private String secret;
    /** How long an issued token stays valid. */
    private long tokenTtlDays = 30;

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getSecret() { return secret; }
    public void setSecret(String v) { this.secret = v; }
    public long getTokenTtlDays() { return tokenTtlDays; }
    public void setTokenTtlDays(long v) { this.tokenTtlDays = v; }
}

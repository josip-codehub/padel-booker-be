package org.padel.booker.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
        String token,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("expires_in") Long expiresIn
) {}

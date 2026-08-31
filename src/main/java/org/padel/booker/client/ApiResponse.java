package org.padel.booker.client;

/** Raw HTTP result from a create-reservation attempt (never throws on 4xx). */
public record ApiResponse(int status, String body) {
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}

package io.collectra.api.shared.error;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() { super("Invalid or expired refresh token"); }
}

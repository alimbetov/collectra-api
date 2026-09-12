package io.collectra.api.communication.application;

public class AttachmentResolutionException extends RuntimeException {
    private final String code;
    private final DeliveryFailureKind kind;

    public AttachmentResolutionException(String code, DeliveryFailureKind kind, String message) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public AttachmentResolutionException(
            String code, DeliveryFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.kind = kind;
    }

    public String code() {
        return code;
    }

    public DeliveryFailureKind kind() {
        return kind;
    }
}

package dev.customgui.transaction;

import java.util.UUID;

public record TransactionResult(UUID transactionId, Status status, String messageKey, int batchSize,
                                CompensationReport compensation) {
    public TransactionResult(UUID transactionId, Status status, String messageKey, int batchSize) {
        this(transactionId, status, messageKey, batchSize, null);
    }
    public enum Status { SUCCESS, REJECTED, BUSY, ROLLED_BACK, FAILED }
}

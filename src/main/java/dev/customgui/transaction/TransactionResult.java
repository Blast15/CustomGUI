package dev.customgui.transaction;

import java.util.UUID;

public record TransactionResult(UUID transactionId, Status status, String messageKey, int batchSize) {
    public enum Status { SUCCESS, REJECTED, BUSY, ROLLED_BACK, FAILED }
}

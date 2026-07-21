package com.banking.transactionservice.entity;

/**
 * Transaction life cycle flow
 * 1. PENDING --> processing-->completed (clean transaction)
 * 2. PENDING --> processing-->pending_verification-->suspicious activity-->verify with sender-->if ok COMPLETED else FLAG the transaction and block the account and SAGA refund (compensate transaction)
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FLAGGED,
    FAILED
}

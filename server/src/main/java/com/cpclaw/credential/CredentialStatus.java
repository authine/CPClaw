package com.cpclaw.credential;

/**
 * Deliberately contains no secret value.  A credential can exist in storage but
 * still be unusable after an encryption-key rotation or an invalid ciphertext.
 */
public enum CredentialStatus {
    MISSING,
    AVAILABLE,
    UNREADABLE
}

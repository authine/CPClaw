package com.cpclaw.credential;

/** Raised when a persisted credential cannot be decrypted with this runtime key. */
public class CredentialUnavailableException extends IllegalStateException {

    public CredentialUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

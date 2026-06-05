package com.cpclaw.credential.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "encrypted_credentials")
public class EncryptedCredential {

    @Id
    private String id;

    @Column(name = "credential_type", nullable = false)
    private String credentialType;

    @Column(name = "credential_owner_type", nullable = false)
    private String credentialOwnerType;

    @Column(name = "credential_owner_id", nullable = false)
    private String credentialOwnerId;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    @Column(nullable = false)
    private String iv;

    @Column(name = "auth_tag", nullable = false)
    private String authTag;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
    public String getCredentialOwnerType() { return credentialOwnerType; }
    public void setCredentialOwnerType(String credentialOwnerType) { this.credentialOwnerType = credentialOwnerType; }
    public String getCredentialOwnerId() { return credentialOwnerId; }
    public void setCredentialOwnerId(String credentialOwnerId) { this.credentialOwnerId = credentialOwnerId; }
    public String getEncryptedValue() { return encryptedValue; }
    public void setEncryptedValue(String encryptedValue) { this.encryptedValue = encryptedValue; }
    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
    public String getAuthTag() { return authTag; }
    public void setAuthTag(String authTag) { this.authTag = authTag; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

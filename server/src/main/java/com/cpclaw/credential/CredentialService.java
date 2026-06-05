package com.cpclaw.credential;

import com.cpclaw.credential.CryptoService.EncryptedValue;
import com.cpclaw.credential.entity.EncryptedCredential;
import com.cpclaw.credential.repository.EncryptedCredentialRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialService {

    private final CryptoService cryptoService;
    private final EncryptedCredentialRepository credentialRepository;

    public CredentialService(CryptoService cryptoService, EncryptedCredentialRepository credentialRepository) {
        this.cryptoService = cryptoService;
        this.credentialRepository = credentialRepository;
    }

    @Transactional
    public Optional<String> saveCredential(String ownerType, String ownerId, String credentialType, String plainValue) {
        if (plainValue == null || plainValue.isBlank()) {
            return Optional.empty();
        }

        EncryptedValue encrypted = cryptoService.encrypt(plainValue);
        Instant now = Instant.now();
        EncryptedCredential credential = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType)
            .orElseGet(() -> {
                EncryptedCredential created = new EncryptedCredential();
                created.setId(UUID.randomUUID().toString());
                created.setCredentialOwnerType(ownerType);
                created.setCredentialOwnerId(ownerId);
                created.setCredentialType(credentialType);
                created.setCreatedAt(now);
                return created;
            });
        credential.setEncryptedValue(encrypted.encryptedValue());
        credential.setIv(encrypted.iv());
        credential.setAuthTag(encrypted.authTag());
        credential.setUpdatedAt(now);
        credentialRepository.save(credential);
        return Optional.of(credential.getId());
    }

    public boolean hasCredential(String ownerType, String ownerId, String credentialType) {
        return credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType).isPresent();
    }

    public Optional<String> revealCredential(String ownerType, String ownerId, String credentialType) {
        return credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType)
            .map(credential -> cryptoService.decrypt(credential.getEncryptedValue(), credential.getIv(), credential.getAuthTag()));
    }
}

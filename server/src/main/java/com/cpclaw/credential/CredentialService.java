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

    public CredentialStatus credentialStatus(String ownerType, String ownerId, String credentialType) {
        return credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType)
            .map(credential -> {
                try {
                    cryptoService.decrypt(credential.getEncryptedValue(), credential.getIv(), credential.getAuthTag());
                    return CredentialStatus.AVAILABLE;
                } catch (IllegalStateException exception) {
                    return CredentialStatus.UNREADABLE;
                }
            })
            .orElse(CredentialStatus.MISSING);
    }

    public Optional<String> revealCredential(String ownerType, String ownerId, String credentialType) {
        return credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType)
            .map(credential -> {
                try {
                    return cryptoService.decrypt(credential.getEncryptedValue(), credential.getIv(), credential.getAuthTag());
                } catch (IllegalStateException exception) {
                    throw new CredentialUnavailableException(unavailableMessage(credentialType), exception);
                }
            });
    }

    @Transactional
    public void deleteCredential(String ownerType, String ownerId, String credentialType) {
        credentialRepository.deleteByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(ownerType, ownerId, credentialType);
    }

    private String unavailableMessage(String credentialType) {
        return switch (credentialType) {
            case "model_api_key" -> "已保存的模型 API Key 无法使用。请在系统设置中重新录入并验证后保存。";
            case "user_cloudpivot_password" -> "已保存的个人云枢账号密码无法使用。请在系统设置中重新输入密码并保存，然后重试。";
            case "admin_cloudpivot_password" -> "已保存的管理员云枢账号密码无法使用。请在系统设置中重新输入密码并保存，然后重试。";
            default -> "已保存的凭据无法使用。请在系统设置中重新录入并保存。";
        };
    }
}

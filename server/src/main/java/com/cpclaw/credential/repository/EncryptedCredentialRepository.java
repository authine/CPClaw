package com.cpclaw.credential.repository;

import com.cpclaw.credential.entity.EncryptedCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptedCredentialRepository extends JpaRepository<EncryptedCredential, String> {

    Optional<EncryptedCredential> findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(
        String credentialOwnerType,
        String credentialOwnerId,
        String credentialType
    );
}

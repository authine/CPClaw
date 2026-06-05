package com.cpclaw.attachment;

import com.cpclaw.attachment.dto.AttachmentResponse;
import com.cpclaw.attachment.entity.Attachment;
import com.cpclaw.attachment.repository.AttachmentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final Path storageRoot;

    public AttachmentService(AttachmentRepository attachmentRepository, @Value("${cpclaw.storage-root:./storage}") String storageRoot) {
        this.attachmentRepository = attachmentRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public AttachmentResponse saveUpload(MultipartFile file) {
        try {
            Files.createDirectories(storageRoot.resolve("attachments"));
            String id = UUID.randomUUID().toString();
            String safeFilename = sanitizeFilename(file.getOriginalFilename());
            Path target = storageRoot.resolve("attachments").resolve(id + "-" + safeFilename).normalize();
            Files.copy(file.getInputStream(), target);
            String sha256 = sha256(target);
            Attachment attachment = new Attachment();
            attachment.setId(id);
            attachment.setOriginalFilename(safeFilename);
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setStoragePath(target.toString());
            attachment.setSha256(sha256);
            attachment.setStatus("pending_extraction");
            attachment.setCreatedAt(Instant.now());
            attachmentRepository.save(attachment);
            return toResponse(attachment);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save attachment", exception);
        }
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
            attachment.getId(),
            attachment.getOriginalFilename(),
            attachment.getContentType(),
            attachment.getFileSize(),
            attachment.getStatus()
        );
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        return filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(OutputStreamDiscard.INSTANCE);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash attachment", exception);
        }
    }

    private static final class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();
        @Override public void write(int b) { }
    }
}

package com.cpclaw.attachment.dto;

public record AttachmentResponse(
    String id,
    String filename,
    String contentType,
    long size,
    String status
) {
}

package com.cpclaw.attachment;

import com.cpclaw.attachment.dto.AttachmentResponse;
import com.cpclaw.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    public ApiResponse<AttachmentResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.saveUpload(file));
    }
}

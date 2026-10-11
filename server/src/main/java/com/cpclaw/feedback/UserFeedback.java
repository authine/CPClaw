package com.cpclaw.feedback;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_feedback")
public class UserFeedback {
    @Id private String id;
    @Column(name="subject_id", nullable=false) private String subjectId;
    @Column(name="tenant_id", nullable=false) private String tenantId;
    @Column(name="app_code") private String appCode;
    @Column(nullable=false) private String category;
    @Column(nullable=false) private String title;
    @Column(nullable=false, columnDefinition="TEXT") private String content;
    @Column(nullable=false) private String status;
    @Column(name="admin_note", length=2000) private String adminNote;
    @Column(name="handled_by") private String handledBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getSubjectId(){return subjectId;} public void setSubjectId(String v){subjectId=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getAppCode(){return appCode;} public void setAppCode(String v){appCode=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getAdminNote(){return adminNote;} public void setAdminNote(String v){adminNote=v;}
    public String getHandledBy(){return handledBy;} public void setHandledBy(String v){handledBy=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}

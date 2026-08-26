package com.cpclaw.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkdownSkillRepository extends JpaRepository<MarkdownSkill, String> {
    List<MarkdownSkill> findAllByPublicationStatusOrderByUpdatedAtDesc(String publicationStatus);
}

package com.cpclaw.insight.template;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportSkillTemplateRepository extends JpaRepository<ReportSkillTemplate, String> {
    List<ReportSkillTemplate> findByEnabledTrueOrderByPriorityDesc();
    java.util.Optional<ReportSkillTemplate> findBySkillCode(String skillCode);
    List<ReportSkillTemplate> findAllByOrderByUpdatedAtDesc();
}

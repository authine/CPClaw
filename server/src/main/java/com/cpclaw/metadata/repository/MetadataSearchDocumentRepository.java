package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.MetadataSearchDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataSearchDocumentRepository extends JpaRepository<MetadataSearchDocument, String> {
    List<MetadataSearchDocument> findTop10BySearchTextContainingIgnoreCaseOrderByCreatedAtDesc(String query);
}

package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.MetadataSearchDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetadataSearchDocumentRepository extends JpaRepository<MetadataSearchDocument, String> {
    @Query(value = """
        select *
        from metadata_search_documents
        where :query = ''
           or instr(:query, name) > 0
           or instr(:query, code) > 0
           or instr(search_text, :query) > 0
        order by
            case
                when instr(:query, name) > 0 then 0
                when instr(:query, code) > 0 then 1
                else 2
            end,
            created_at desc
        limit 10
        """, nativeQuery = true)
    List<MetadataSearchDocument> searchByText(@Param("query") String query);
}

package com.cpclaw.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import com.cpclaw.vector.MetadataVectorSearch;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataSearchServiceTests {

    @Test
    void prefersBusinessEntityOverSameNamedReferenceField() {
        MetadataSearchDocumentRepository repository = mock(MetadataSearchDocumentRepository.class);
        MetadataVectorSearch vectorSearch = mock(MetadataVectorSearch.class);
        MetadataSearchDocument leadFieldOnProject = document(
            "data-item", "data_item", "线索", "clue", "CRM / 售前项目 / 线索"
        );
        MetadataSearchDocument leadEntity = document(
            "lead-entity", "entity", "私海线索", "sql_line", "CRM / 私海线索"
        );
        when(vectorSearch.enabled()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(leadFieldOnProject, leadEntity));
        when(repository.searchByText(anyString())).thenReturn(List.of(leadFieldOnProject));

        MetadataSearchResult result = new MetadataSearchService(repository, vectorSearch)
            .bestMatch("帮我创建一个线索");

        assertEquals("entity", result.objectType());
        assertEquals("私海线索", result.name());
        assertEquals("sql_line", result.code());
    }

    private MetadataSearchDocument document(String id, String objectType, String name, String code, String graphPath) {
        MetadataSearchDocument value = new MetadataSearchDocument();
        value.setId(id);
        value.setObjectType(objectType);
        value.setObjectId(id);
        value.setName(name);
        value.setCode(code);
        value.setGraphPath(graphPath);
        value.setSearchText(name + " " + code + " " + graphPath);
        value.setRiskLevel("low");
        return value;
    }
}

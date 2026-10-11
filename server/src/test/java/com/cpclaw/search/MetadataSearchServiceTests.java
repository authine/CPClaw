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

    @Test
    void quotedEntityNameBeatsShorterSamePrefixEntity() {
        MetadataSearchDocument broad = document("broad", "entity", "测试", "test008", "测试应用 / 测试");
        MetadataSearchDocument exact = document("exact", "entity", "测试多分支激活", "test_multi_branch", "测试应用 / 测试多分支激活");
        MetadataSearchDocumentRepository repository = mock(MetadataSearchDocumentRepository.class);
        MetadataVectorSearch vectorSearch = mock(MetadataVectorSearch.class);
        when(vectorSearch.enabled()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(broad, exact));
        when(repository.searchByText(anyString())).thenReturn(List.of(broad, exact));

        MetadataSearchResult result = new MetadataSearchService(repository, vectorSearch)
                .bestMatch("查询“测试多分支激活”中的记录");

        assertEquals("测试多分支激活", result.name());
        assertEquals("test_multi_branch", result.code());
    }

    @Test
    void compoundAnalysisTargetPrefersEntityContainedInTheBusinessPhrase() {
        MetadataSearchDocument unrelatedDeliverable = document("exception", "entity", "异常", "abnormalproject", "项目管理 / 异常项目清单");
        MetadataSearchDocument opportunity = document("opportunity", "entity", "商机", "int_bu_oppor", "zlcsstcrm / 商机");
        MetadataSearchDocument lead = document("lead", "entity", "线索", "sql_line", "zlcsstcrm / 私海线索");
        MetadataSearchDocumentRepository repository = mock(MetadataSearchDocumentRepository.class);
        MetadataVectorSearch vectorSearch = mock(MetadataVectorSearch.class);
        when(vectorSearch.enabled()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(unrelatedDeliverable, opportunity, lead));
        when(repository.searchByText(anyString())).thenReturn(List.of(unrelatedDeliverable, opportunity, lead));

        MetadataSearchResult result = new MetadataSearchService(repository, vectorSearch)
            .bestMatch("分析本月线索转商机情况，并给出异常、趋势和分析依据");

        assertEquals("entity", result.objectType());
        assertEquals("商机", result.name());
        assertEquals("int_bu_oppor", result.code());
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

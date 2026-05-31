package me.rainhouse.qasystem.common.dto;

import lombok.Builder;
import lombok.Data;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;

import java.util.List;

@Data
@Builder
public class AiChatResponse {
    private String originalQuestion;
    private String rewriteQuestion;
    private String moduleType;
    private Integer hitStatus;
    private String hitLabel;
    private Double topScore;
    private Long topKnowledgeId;
    private String answer;
    private String answerSource;
    private Long responseTimeMs;
    private List<VectorSearchResult> references;
}

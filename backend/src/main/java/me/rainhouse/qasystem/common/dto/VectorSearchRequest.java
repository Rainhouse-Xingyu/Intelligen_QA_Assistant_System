package me.rainhouse.qasystem.common.dto;

import lombok.Data;

@Data
public class VectorSearchRequest {
    private String query;
    private String moduleType;
    private Integer topK;
    private Long sessionId;
}

package me.rainhouse.qasystem.common.dto;

import lombok.Data;

@Data
public class AiChatRequest {
    private String query;
    private String moduleType;
    private Boolean needTts;
}

package me.rainhouse.qasystem.service;

import me.rainhouse.qasystem.common.dto.AiChatResponse;

public interface AiChatService {

    AiChatResponse chat(Long userId, Long sessionId, String query, String selectedModuleType);
}

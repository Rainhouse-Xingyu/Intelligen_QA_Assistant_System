package me.rainhouse.qasystem.service.kb;

public record StructuredFaqItem(
        String categoryL1,
        String categoryL2,
        String categoryL3,
        String question,
        String answer,
        String templateCode
) {
}

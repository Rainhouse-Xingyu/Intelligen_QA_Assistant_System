package me.rainhouse.qasystem.service.kb;

public record FaqItem(
        String categoryL1,
        String categoryL2,
        String categoryL3,
        String question,
        String answer
) {
    public FaqItem(String question, String answer) {
        this(null, null, null, question, answer);
    }
}

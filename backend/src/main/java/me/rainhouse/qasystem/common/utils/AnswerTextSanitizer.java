package me.rainhouse.qasystem.common.utils;

public final class AnswerTextSanitizer {

    private static final String REFERENCE_MARKER = "参考来源";
    private static final String AI_NOTICE_MARKER = "以上答复含有AI解读成分";
    private static final String GREETING_PREFIX = "同学，你好！";
    private static final String ANSWER_PREFIX = "您所咨询的问题解答如下：";

    private AnswerTextSanitizer() {
    }

    /**
     * 移除面向用户的答案中可能被兜底逻辑或模型生成的参考来源段落。
     * 检索结果本身仍通过 AiChatResponse.references 保留，不影响后台追踪。
     */
    public static String stripReferenceSection(String text) {
        if (text == null) {
            return "";
        }
        String result = text.trim();
        int markerIndex = result.indexOf(REFERENCE_MARKER);
        if (markerIndex >= 0) {
            result = result.substring(0, markerIndex);
        }
        return result.trim();
    }

    /**
     * 生成较短的 TTS 文本：保留核心答案，去掉界面礼貌话术、免责声明和参考来源。
     */
    public static String forSpeech(String text) {
        String result = stripReferenceSection(text);
        if (result.startsWith(GREETING_PREFIX)) {
            result = result.substring(GREETING_PREFIX.length()).trim();
        }
        if (result.startsWith(ANSWER_PREFIX)) {
            result = result.substring(ANSWER_PREFIX.length()).trim();
        }

        int noticeIndex = result.indexOf(AI_NOTICE_MARKER);
        if (noticeIndex >= 0) {
            result = result.substring(0, noticeIndex).trim();
        }
        result = removeEnding(result, "祝您考试取得好成绩！");
        result = removeEnding(result, "谢谢！");
        return result.trim();
    }

    private static String removeEnding(String text, String ending) {
        if (text.endsWith(ending)) {
            return text.substring(0, text.length() - ending.length()).trim();
        }
        return text;
    }
}

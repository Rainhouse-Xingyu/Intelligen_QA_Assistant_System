package me.rainhouse.qasystem.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.service.AudioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AudioServiceImpl implements AudioService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String appId;
    private final String apiKey;
    private final String accessToken;
    private final String asrEndpoint;
    private final String asrQueryEndpoint;
    private final String asrResourceId;
    private final long asrPollIntervalMs;
    private final long asrPollTimeoutMs;
    private final String ttsEndpoint;
    private final String ttsResourceId;
    private final String ttsSpeaker;
    private final String outputDir;
    private final String publicPath;

    public AudioServiceImpl(RestTemplate restTemplate,
                            @Value("${volcengine.speech.app-id:}") String appId,
                            @Value("${volcengine.speech.api-key:}") String apiKey,
                            @Value("${volcengine.speech.access-token:}") String accessToken,
                            @Value("${volcengine.speech.asr.endpoint}") String asrEndpoint,
                            @Value("${volcengine.speech.asr.query-endpoint}") String asrQueryEndpoint,
                            @Value("${volcengine.speech.asr.resource-id}") String asrResourceId,
                            @Value("${volcengine.speech.asr.poll-interval-ms:1500}") long asrPollIntervalMs,
                            @Value("${volcengine.speech.asr.poll-timeout-ms:30000}") long asrPollTimeoutMs,
                            @Value("${volcengine.speech.tts.endpoint}") String ttsEndpoint,
                            @Value("${volcengine.speech.tts.resource-id}") String ttsResourceId,
                            @Value("${volcengine.speech.tts.speaker}") String ttsSpeaker,
                            @Value("${volcengine.speech.tts.output-dir:uploads/audio}") String outputDir,
                            @Value("${volcengine.speech.tts.public-path:/media/audio}") String publicPath) {
        this.restTemplate = restTemplate;
        this.appId = appId;
        this.apiKey = apiKey;
        this.accessToken = accessToken;
        this.asrEndpoint = asrEndpoint;
        this.asrQueryEndpoint = asrQueryEndpoint;
        this.asrResourceId = asrResourceId;
        this.asrPollIntervalMs = asrPollIntervalMs;
        this.asrPollTimeoutMs = asrPollTimeoutMs;
        this.ttsEndpoint = ttsEndpoint;
        this.ttsResourceId = ttsResourceId;
        this.ttsSpeaker = ttsSpeaker;
        this.outputDir = outputDir;
        this.publicPath = publicPath;
    }

    @Override
    public String speechToText(MultipartFile audioFile) {
        validateCredentials();
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }

        try {
            String requestId = UUID.randomUUID().toString();
            Map<String, Object> body = buildAsrRequest(audioFile);
            JsonNode response = isAsyncAsrEndpoint()
                    ? submitAndPollAsr(body, requestId)
                    : recognizeFlashAsr(body, requestId);

            String text = response == null ? null : response.path("result").path("text").asText(null);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("火山 ASR 未返回识别文本");
            }
            return text.trim();
        } catch (HttpStatusCodeException e) {
            throw speechServiceException("ASR", e);
        } catch (IOException e) {
            throw new IllegalStateException("读取音频文件失败", e);
        }
    }

    @Override
    public String textToSpeech(String text) {
        validateCredentials();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("待合成文本不能为空");
        }

        try {
            byte[] audioBytes = synthesizeSpeech(text.trim());
            String fileName = UUID.randomUUID() + ".mp3";
            Path outputPath = Path.of(outputDir).toAbsolutePath().normalize();
            Files.createDirectories(outputPath);
            Files.write(outputPath.resolve(fileName), audioBytes);
            return normalizedPublicPath() + "/" + fileName;
        } catch (IOException e) {
            throw new IllegalStateException("保存 TTS 音频文件失败", e);
        }
    }

    private Map<String, Object> buildAsrRequest(MultipartFile audioFile) throws IOException {
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("data", Base64.getEncoder().encodeToString(audioFile.getBytes()));
        audio.put("format", detectAudioFormat(audioFile));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model_name", "bigmodel");
        request.put("enable_itn", true);
        request.put("enable_punc", true);
        request.put("enable_ddc", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", Map.of("uid", speechUid()));
        body.put("audio", audio);
        body.put("request", request);
        return body;
    }

    private JsonNode recognizeFlashAsr(Map<String, Object> body, String requestId) {
        SpeechResponse response = postSpeechJson(asrEndpoint, asrResourceId, requestId, body);
        if (!"20000000".equals(response.code())) {
            throw speechServiceFailure("ASR", response);
        }
        return response.payload();
    }

    private JsonNode submitAndPollAsr(Map<String, Object> body, String requestId) {
        SpeechResponse submitted = postSpeechJson(asrEndpoint, asrResourceId, requestId, body);
        if (!"20000000".equals(submitted.code())) {
            throw speechServiceFailure("ASR", submitted);
        }

        long deadline = System.currentTimeMillis() + Math.max(1000, asrPollTimeoutMs);
        while (System.currentTimeMillis() <= deadline) {
            SpeechResponse queried = postSpeechJson(asrQueryEndpoint, asrResourceId, requestId, Map.of());
            String code = queried.code();
            if ("20000000".equals(code)) {
                return queried.payload();
            }
            if (!"20000001".equals(code) && !"20000002".equals(code)) {
                throw speechServiceFailure("ASR", queried);
            }
            sleepQuietly(Math.max(300, asrPollIntervalMs));
        }
        throw new IllegalStateException("火山 ASR 识别超时，请稍后重试或调大 VOLCENGINE_ASR_POLL_TIMEOUT_MS");
    }

    private SpeechResponse postSpeechJson(String endpoint, String resourceId, String requestId, Map<String, Object> body) {
        return restTemplate.execute(
                endpoint,
                org.springframework.http.HttpMethod.POST,
                request -> {
                    request.getHeaders().addAll(buildSpeechHeaders(resourceId, requestId));
                    request.getBody().write(objectMapper.writeValueAsBytes(body == null ? Map.of() : body));
                },
                response -> {
                    JsonNode payload = null;
                    try {
                        payload = objectMapper.readTree(response.getBody());
                    } catch (Exception ignored) {
                        // Some submit responses carry status only in headers.
                    }
                    String code = firstText(
                            response.getHeaders().getFirst("X-Api-Status-Code"),
                            payload == null ? "" : payload.path("header").path("code").asText(""));
                    String message = firstText(
                            response.getHeaders().getFirst("X-Api-Message"),
                            payload == null ? "" : payload.path("header").path("message").asText(""));
                    String logId = firstText(
                            response.getHeaders().getFirst("X-Tt-Logid"),
                            payload == null ? "" : payload.path("header").path("log_id").asText(""));
                    return new SpeechResponse(code, message, logId, payload);
                }
        );
    }

    private boolean isAsyncAsrEndpoint() {
        return StringUtils.hasText(asrEndpoint) && asrEndpoint.contains("/submit");
    }

    private IllegalStateException speechServiceFailure(String serviceName, SpeechResponse response) {
        return new IllegalStateException("火山 " + serviceName + " 调用失败: code="
                + response.code() + ", message=" + response.message() + ", logId=" + response.logId());
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String speechUid() {
        return StringUtils.hasText(appId) ? appId : "qasystem";
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("火山 ASR 识别被中断", e);
        }
    }

    private byte[] synthesizeSpeech(String text) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = buildTtsRequest(text);

        try {
            return restTemplate.execute(
                    ttsEndpoint,
                    org.springframework.http.HttpMethod.POST,
                    request -> {
                        request.getHeaders().addAll(buildSpeechHeaders(ttsResourceId, requestId));
                        request.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
                        request.getBody().write(objectMapper.writeValueAsBytes(body));
                    },
                    response -> {
                        String logId = response.getHeaders().getFirst("X-Tt-Logid");
                        ByteArrayOutputStream audio = new ByteArrayOutputStream();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                consumeTtsLine(line, audio);
                            }
                        }
                        byte[] audioBytes = audio.toByteArray();
                        if (audioBytes.length == 0) {
                            throw new IllegalStateException("火山 TTS 未返回音频数据，logId=" + logId);
                        }
                        return audioBytes;
                    }
            );
        } catch (HttpStatusCodeException e) {
            throw speechServiceException("TTS", e);
        }
    }

    private Map<String, Object> buildTtsRequest(String text) {
        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("format", "mp3");
        audioParams.put("sample_rate", 24000);
        audioParams.put("speech_rate", 0);
        audioParams.put("loudness_rate", 0);

        Map<String, Object> reqParams = new LinkedHashMap<>();
        reqParams.put("text", text);
        reqParams.put("speaker", ttsSpeaker);
        reqParams.put("audio_params", audioParams);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", Map.of("uid", speechUid()));
        body.put("namespace", "BidirectionalTTS");
        body.put("req_params", reqParams);
        return body;
    }

    private HttpHeaders buildSpeechHeaders(String resourceId, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            headers.add("X-Api-Key", apiKey.trim());
        } else {
            headers.add("X-Api-App-Key", appId);
            headers.add("X-Api-Access-Key", accessToken);
        }
        headers.add("X-Api-Resource-Id", resourceId);
        headers.add("X-Api-Request-Id", requestId);
        headers.add("X-Api-Sequence", "-1");
        return headers;
    }

    private IllegalStateException speechServiceException(String serviceName, HttpStatusCodeException e) {
        String message = parseSpeechErrorMessage(e.getResponseBodyAsString());
        String detail = StringUtils.hasText(message) ? message : e.getStatusText();
        if (e.getStatusCode().value() == 403 && detail.contains("requested resource not granted")) {
            detail = detail + "。请在火山引擎控制台开通对应资源，或把 VOLCENGINE_ASR_RESOURCE_ID / VOLCENGINE_TTS_RESOURCE_ID 调整为账号已开通的资源 ID。";
        }
        return new IllegalStateException("火山 " + serviceName + " 调用失败: HTTP " + e.getStatusCode().value() + ", " + detail, e);
    }

    private String parseSpeechErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("header").path("message").asText("");
            if (StringUtils.hasText(message)) {
                return message;
            }
            return root.path("message").asText("");
        } catch (Exception ignored) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }

    private void consumeTtsLine(String line, ByteArrayOutputStream audio) throws IOException {
        String payload = line == null ? "" : line.trim();
        if (payload.isEmpty() || payload.startsWith(":")) {
            return;
        }
        if (!payload.startsWith("data:")) {
            return;
        }
        payload = payload.substring("data:".length()).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }

        JsonNode node = objectMapper.readTree(payload);
        JsonNode code = node.get("code");
        if (code != null && code.isNumber() && code.asInt() != 0 && code.asInt() != 20000000) {
            throw new IllegalStateException("火山 TTS 调用失败: code=" + code.asInt() + ", message=" + node.path("message").asText());
        }
        for (String base64Audio : collectAudioPayloads(node)) {
            audio.write(Base64.getDecoder().decode(base64Audio));
        }
    }

    private List<String> collectAudioPayloads(JsonNode node) {
        List<String> payloads = new ArrayList<>();
        collectAudioPayloads(node, payloads);
        return payloads;
    }

    private void collectAudioPayloads(JsonNode node, List<String> payloads) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isTextual() && ("data".equals(key) || "audio".equals(key) || "audio_data".equals(key))) {
                    String textValue = value.asText();
                    if (looksLikeBase64Audio(textValue)) {
                        payloads.add(textValue);
                    }
                } else {
                    collectAudioPayloads(value, payloads);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectAudioPayloads(child, payloads));
        }
    }

    private boolean looksLikeBase64Audio(String value) {
        return StringUtils.hasText(value) && value.length() > 32 && value.matches("^[A-Za-z0-9+/=\\r\\n]+$");
    }

    private String detectAudioFormat(MultipartFile audioFile) {
        String filename = audioFile.getOriginalFilename();
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            if ("wav".equals(ext) || "mp3".equals(ext) || "ogg".equals(ext)
                    || "opus".equals(ext) || "m4a".equals(ext) || "aac".equals(ext)
                    || "amr".equals(ext)) {
                return "opus".equals(ext) ? "ogg" : ext;
            }
        }
        String contentType = audioFile.getContentType();
        if (contentType != null && contentType.contains("wav")) {
            return "wav";
        }
        if (contentType != null && contentType.contains("ogg")) {
            return "ogg";
        }
        if (contentType != null && contentType.contains("mp4")) {
            return "m4a";
        }
        if (contentType != null && contentType.contains("aac")) {
            return "aac";
        }
        return "mp3";
    }

    private void validateCredentials() {
        if (StringUtils.hasText(apiKey)) {
            return;
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("火山引擎语音服务未配置 API Key，或旧版 APP ID / Access Token");
        }
    }

    private String normalizedPublicPath() {
        if (!StringUtils.hasText(publicPath)) {
            return "/media/audio";
        }
        String path = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private record SpeechResponse(String code, String message, String logId, JsonNode payload) {
    }
}

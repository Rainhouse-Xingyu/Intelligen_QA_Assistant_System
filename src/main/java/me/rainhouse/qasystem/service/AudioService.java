package me.rainhouse.qasystem.service;

import org.springframework.web.multipart.MultipartFile;

public interface AudioService {

    /**
     * 语音转文字 (Speech to Text)
     * @param audioFile 录音文件
     * @return 识别出的文本内容
     */
    String speechToText(MultipartFile audioFile);

    /**
     * 文字转语音 (Text to Speech)
     * @param text 文本内容
     * @return 语音文件的 URL，或基于 base64 编码的音频流
     */
    String textToSpeech(String text);
}
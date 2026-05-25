package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.service.AudioService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class AudioServiceImpl implements AudioService {

    @Override
    public String speechToText(MultipartFile audioFile) {
        // TODO: 对接外部的大模型语音插件（Coze Audio ASR / 百度 / 阿里）获取文本
        // 这里目前通过 mock 模拟识别结果
        long size = audioFile.getSize();
        log.info("接收到音频文件，大小：{} bytes，即将调用云端 ASR 服务", size);

        // 模拟返回
        return "由于目前AIGC大作业暂未接入统一ASR，这是系统模拟返回的识别文本。";
    }

    @Override
    public String textToSpeech(String text) {
        // TODO: 对接大语言模型的 TTS 语音合成接口
        // 将文本发送给生成端，由生成端返回 MP3 的 URL 或 Base64 流供前端播放。
        // （若使用 iOS 原生 AVKit，前端可以直接接受 URL）
        
        log.info("需将该段文字转换为语音流播报: {}", text);

        return "https://mock-audio-url.com/aigc-audio-reply.mp3";
    }
}
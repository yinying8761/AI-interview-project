package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.ai.AiApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 语音合成代理：前端把题目文本发到本接口，由本接口转调 Python /api/ai/tts 并回传音频字节。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class TtsController {

    private final AiApiClient aiApiClient;

    /** 合成语音，返回音频字节（mpeg）。 */
    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String voice = body.get("voice");
        byte[] audio = aiApiClient.synthesize(text, voice);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("Content-Disposition", "inline")
                .body(audio);
    }
}

package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.dto.AsrResponse;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.interview.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 语音转写控制器：上传音频并触发转写。
 */
@RestController
@RequestMapping("/api/transcribe")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    /** 上传音频片段并转写，返回转写结果并按题目/片段落库。 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/{segmentId}")
    public Result<AsrResponse> transcribe(@PathVariable Long segmentId,
                                          @RequestParam Long recordId,
                                          @RequestParam Integer questionIndex,
                                          @RequestParam MultipartFile audio) throws IOException {
        // 空白或过短音频不送转写，避免路径/模型异常
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(400, "音频内容为空");
        }
        return Result.success(transcriptionService.transcribeAndSave(
                recordId, questionIndex, segmentId, audio.getBytes()));
    }
}

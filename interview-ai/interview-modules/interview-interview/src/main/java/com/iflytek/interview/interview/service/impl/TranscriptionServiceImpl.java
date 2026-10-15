package com.iflytek.interview.interview.service.impl;

import com.iflytek.interview.common.ai.AiApiClient;
import com.iflytek.interview.common.dto.AsrResponse;
import com.iflytek.interview.interview.entity.InterviewTranscript;
import com.iflytek.interview.interview.entity.InterviewTranscriptSegment;
import com.iflytek.interview.interview.mapper.InterviewTranscriptMapper;
import com.iflytek.interview.interview.mapper.InterviewTranscriptSegmentMapper;
import com.iflytek.interview.interview.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 语音转写服务实现：调用 Python，后处理并落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final AiApiClient aiApiClient;
    private final InterviewTranscriptMapper transcriptMapper;
    private final InterviewTranscriptSegmentMapper segmentMapper;

    @Override
    @Transactional
    public AsrResponse transcribeAndSave(Long recordId, Integer questionIndex,
                                         Long segmentId, byte[] audioBytes) {
        // 1) 调用 Python ASR
        AsrResponse asr = aiApiClient.transcribe(audioBytes);

        // 2) 简单的文本后处理
        String text = postProcess(asr.getText());

        // 3) 回写主表
        InterviewTranscript tr = new InterviewTranscript();
        tr.setRecordId(recordId);
        tr.setQuestionIndex(questionIndex);
        tr.setSegmentId(segmentId);
        tr.setText(text);
        tr.setLanguage(asr.getLanguage());
        tr.setStatus("PARSED");
        transcriptMapper.insert(tr);

        // 4) 分段落库
        if (asr.getSegments() != null) {
            int seq = 0;
            for (AsrResponse.AsrSegment s : asr.getSegments()) {
                InterviewTranscriptSegment seg = new InterviewTranscriptSegment();
                seg.setTranscriptId(tr.getId());
                seg.setSeq(++seq);
                // 秒 -> 毫秒
                seg.setStartMs((long) (s.getStart() * 1000));
                seg.setEndMs((long) (s.getEnd() * 1000));
                seg.setText(s.getText());
                segmentMapper.insert(seg);
            }
        }

        log.info("转写完成: recordId={}, questionIndex={}, segmentId={}, chars={}",
                recordId, questionIndex, segmentId, text.length());
        return asr;
    }

    /** 简单的文本后处理，可扩展标点恢复、去噪等。 */
    private String postProcess(String raw) {
        return raw == null ? "" : raw.trim();
    }
}

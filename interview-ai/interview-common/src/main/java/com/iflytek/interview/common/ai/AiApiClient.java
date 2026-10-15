package com.iflytek.interview.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.dto.AsrResponse;
import com.iflytek.interview.common.dto.ResumeAiParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Python AI 服务的 HTTP 客户端。
 * 所有调用面向 Java 后端，不面向浏览器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiApiClient {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * 创建一场 AI 面试会话。
     * @param externalId Java 侧业务记录 ID（如 interview_record.id）
     * @param userId     候选人 Java 用户 ID
     * @return Python 会话 ID，Java 侧保存为 ai_session_id
     */
    public String createSession(Long externalId, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("external_id", String.valueOf(externalId));
        body.put("user_id", String.valueOf(userId));
        body.put("job_role", "");
        body.put("difficulty", "中级");
        body.put("voice", aiProperties.getDefaultVoice());
        body.put("auto_tts", false);
        body.put("follow_up_mode", "random");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions",
                HttpMethod.POST, entity, Map.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(502, "创建 AI 会话失败: " + resp.getStatusCode());
        }
        Object id = resp.getBody() == null ? null : resp.getBody().get("id");
        if (id == null) {
            throw new BusinessException(502, "AI 会话响应缺少 id 字段");
        }
        log.info("已创建 AI 会话: externalId={}, aiSessionId={}", externalId, id);
        return String.valueOf(id);
    }

    /**
     * 删除 / 停止一场 AI 面试会话，用于取消或结束后清理资源。
     */
    public void deleteSession(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId,
                HttpMethod.DELETE, entity, Map.class);
        log.info("已清理 AI 会话: aiSessionId={}", sessionId);
    }

    /**
     * 将视频帧以 multipart 图片转发给 Python 视觉分析。
     * @return Python 返回的实时视觉指标
     */
    public Map<String, Object> submitFrame(String sessionId, byte[] imageBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "frame.jpg";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId + "/frames",
                HttpMethod.POST, entity, Map.class);
        return requireBody(resp, "视觉指标转写失败");
    }

    /**
     * 读取 Python 会话的实时表情与注意力指标。
     */
    public Map<String, Object> getMetrics(String sessionId) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId + "/metrics",
                HttpMethod.GET, entity, Map.class);
        return requireBody(resp, "实时视觉指标读取失败");
    }

    /**
     * 结束 Python 视觉分析并读取 video_summary。
     */
    public Map<String, Object> finish(String sessionId) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId + "/finish",
                HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = requireBody(resp, "视觉分析汇总失败");
        // Python /finish 返回 {"report": {...报告...}}，报告内的 video_summary
        // 才是表情与注意力汇总，此处拆掉外壳，调用方直接面向报告字段。
        Object report = body.get("report");
        if (report instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrapped = (Map<String, Object>) map;
            return unwrapped;
        }
        return body;
    }

    /**
     * 生成开场题，使 Python 会话进入可答题状态。
     * 未生成开场题时，后续 /messages 与 /finish 都会被拒绝。
     */
    public void generateOpening(String sessionId) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(new HashMap<>(), headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId + "/opening",
                HttpMethod.POST, entity, Map.class);
        requireBody(resp, "生成开场题失败");
    }

    /**
     * 把候选人回答提交给 Python，供逐题评估与动态追问使用。
     * 不提交回答会导致 /finish 拿不到 question_evaluation，进而无法产出问答得分。
     */
    public Map<String, Object> sendMessage(String sessionId, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/sessions/" + sessionId + "/messages",
                HttpMethod.POST, entity, Map.class);
        return requireBody(resp, "提交回答失败");
    }

    /**
     * 评测外部传入的问答记录（不经过 Python 会话出题流程）。
     *
     * <p>本地题库与 Python 出题系统并不同源，若复用会话 /finish，大模型会拿
     * Python 自己的题目去配对考生对本地题目的回答，产生“答非所问”的误判，
     * 使报告与输入不对应。此接口把真实“题目 + 回答”直接送评，保证可追溯。
     *
     * @param qaPairs      [{"question": "...", "answer": "..."}]
     * @param jobRole      目标岗位，用于评估岗位匹配度
     * @param difficulty   面试难度
     * @param videoSummary 视频维度汇总，可为 null
     */
    public Map<String, Object> evaluateTranscript(List<Map<String, String>> qaPairs,
                                                  String jobRole,
                                                  String difficulty,
                                                  Map<String, Object> videoSummary) {
        Map<String, Object> body = new HashMap<>();
        body.put("qa_pairs", qaPairs);
        body.put("job_role", jobRole == null ? "" : jobRole);
        body.put("difficulty", difficulty == null ? "中级" : difficulty);
        if (videoSummary != null && !videoSummary.isEmpty()) {
            body.put("video_summary", videoSummary);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/evaluate",
                HttpMethod.POST, entity, Map.class);
        return requireBody(resp, "评测问答记录失败");
    }

    /**
     * 探活：判断 Python AI 服务是否可用。
     */
    public boolean health() {
        try {
            HttpHeaders headers = new HttpHeaders();
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiProperties.getBaseUrl() + "/api/ai/health",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Python AI 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 把音频字节传给 Python /api/ai/asr，返回转写结果。
     */
    public AsrResponse transcribe(byte[] audioBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/asr",
                HttpMethod.POST, entity, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(502, "语音转写失败: HTTP " + resp.getStatusCode());
        }
        if (resp.getBody() == null) {
            throw new BusinessException(502, "语音转写返回为空");
        }
        return objectMapper.convertValue(resp.getBody(), AsrResponse.class);
    }

    /**
     * 把题目文本合成语音，返回音频字节。
     */
    public byte[] synthesize(String text, String voice) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("voice", voice == null || voice.isBlank() ? aiProperties.getDefaultVoice() : voice);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/tts",
                HttpMethod.POST, entity, byte[].class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(502, "语音合成失败: HTTP " + resp.getStatusCode());
        }
        return resp.getBody();
    }

    /**
     * 简历 AI 解析：把简历文件以 multipart file 转发给 Python /api/ai/resume/parse。
     */
    public ResumeAiParseResult parseResume(MultipartFile file) throws java.io.IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        byte[] bytes = file.getBytes();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(file.getOriginalFilename())
                        ? file.getOriginalFilename() : "resume.pdf";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                aiProperties.getBaseUrl() + "/api/ai/resume/parse",
                HttpMethod.POST, entity, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(502, "简历 AI 解析失败: HTTP " + resp.getStatusCode());
        }
        if (resp.getBody() == null) {
            throw new BusinessException(502, "简历 AI 解析返回为空");
        }
        Object parsed = resp.getBody().get("parsed");
        return objectMapper.convertValue(parsed, ResumeAiParseResult.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireBody(ResponseEntity<Map> response, String message) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(502, message + ": HTTP " + response.getStatusCode());
        }
        if (response.getBody() == null) {
            throw new BusinessException(502, message + ": 返回为空");
        }
        return (Map<String, Object>) response.getBody();
    }
}

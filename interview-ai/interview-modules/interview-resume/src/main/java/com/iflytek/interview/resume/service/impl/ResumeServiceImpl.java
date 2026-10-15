package com.iflytek.interview.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.ai.AiApiClient;
import com.iflytek.interview.common.dto.ResumeAiParseResult;
import com.iflytek.interview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import com.iflytek.interview.file.util.FileValidator;
import com.iflytek.interview.file.util.MinioUtil;
import com.iflytek.interview.resume.entity.Resume;
import com.iflytek.interview.resume.entity.ResumeKeyword;
import com.iflytek.interview.resume.entity.ResumeVersion;
import com.iflytek.interview.resume.mapper.ResumeKeywordMapper;
import com.iflytek.interview.resume.mapper.ResumeMapper;
import com.iflytek.interview.resume.mapper.ResumeVersionMapper;
import com.iflytek.interview.resume.parser.ResumeParser;
import com.iflytek.interview.resume.service.ResumeService;
import com.iflytek.interview.resume.vo.ResumeParseResult;
import com.iflytek.interview.resume.vo.ResumeVO;
import com.iflytek.interview.resume.vo.ResumeVersionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    @Autowired
    private MinioUtil minioUtil;
    @Autowired
    private FileValidator fileValidator;
    @Autowired
    private ResumeVersionMapper resumeVersionMapper;
    @Autowired
    private ResumeKeywordMapper resumeKeywordMapper;
    @Autowired
    private ResumeParser resumeParser;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiApiClient aiApiClient;

    private static final int PRESIGN_EXPIRY_SECONDS = 600;

    private static final Set<String> ALLOWED_CONTENT_TYPE = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Override
    @Transactional
    public ResumeVO uploadAndParse(Long userId, MultipartFile file) throws IOException {
        fileValidator.validate(file);
        validateContentType(file.getContentType());

        String originalName = file.getOriginalFilename();
        String fileExt = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();

        Resume resume = getOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .last("LIMIT 1")
        );

        int nextVersion = 1;
        if (resume == null) {
            resume = new Resume();
            resume.setUserId(userId);
            resume.setCurrentVersion(1);
            save(resume);
        } else {
            Long count = resumeVersionMapper.selectCount(
                    new LambdaQueryWrapper<ResumeVersion>()
                            .eq(ResumeVersion::getResumeId, resume.getId())
            );
            nextVersion = count.intValue() + 1;
            resume.setCurrentVersion(nextVersion);
            updateById(resume);
        }

        String objectName = "resume/" + userId + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "_" + originalName;

        minioUtil.upload(objectName, file.getInputStream(), file.getContentType());

        ResumeVersion version = new ResumeVersion();
        version.setResumeId(resume.getId());
        version.setUserId(userId);
        version.setVersion(nextVersion);
        version.setFileName(originalName);
        version.setObjectName(objectName);
        version.setFileSize(file.getSize());
        version.setContentType(file.getContentType());
        version.setFileType(fileExt.toUpperCase());
        version.setStatus("UPLOADED");
        resumeVersionMapper.insert(version);

        ResumeParseResult parseResult;
        try {
            // 优先走 Python AI 解析：Python 负责提取文本 + DeepSeek 结构化解析。
            ResumeAiParseResult ai = aiApiClient.parseResume(file);
            parseResult = toLocalParseResult(ai);
        } catch (Exception e) {
            // 回退：使用本地 Tika 提取 + 正则解析，确保上传流程不中断。
            log.warn("简历 AI 解析失败，回退本地解析: {}", e.getMessage());
            String text = resumeParser.extractText(file);
            parseResult = resumeParser.parse(text);
        }

        resume.setParsedName(parseResult.getName());
        resume.setParsedPhone(parseResult.getPhone());
        resume.setParsedEmail(parseResult.getEmail());
        resume.setEducationSummary(toJson(parseResult.getEducationList()));
        resume.setSkillSummary(toJson(parseResult.getSkills()));
        resume.setProjectSummary(toJson(parseResult.getProjects()));
        resume.setStatus("PARSED");
        updateById(resume);

        saveKeywords(resume.getId(), userId, parseResult.getKeywords());

        version.setStatus("PARSED");
        resumeVersionMapper.updateById(version);

        ResumeVO vo = new ResumeVO();
        vo.setResumeId(resume.getId());
        vo.setVersion(nextVersion);
        vo.setFileName(originalName);
        vo.setFileUrl(minioUtil.getPresignedUrl(objectName, PRESIGN_EXPIRY_SECONDS));
        vo.setParseResult(parseResult);
        return vo;
    }

    @Override
    public ResumeVO getCurrent(Long userId) {
        Resume resume = getOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (resume == null) {
            return null;
        }
        ResumeVersion latest = resumeVersionMapper.selectOne(
                new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resume.getId())
                        .orderByDesc(ResumeVersion::getVersion)
                        .last("LIMIT 1")
        );
        ResumeParseResult parseResult = buildParseResult(resume);
        ResumeVO vo = new ResumeVO();
        vo.setResumeId(resume.getId());
        vo.setVersion(latest == null ? resume.getCurrentVersion() : latest.getVersion());
        vo.setFileName(latest == null ? null : latest.getFileName());
        vo.setFileUrl(latest == null ? null
                : minioUtil.getPresignedUrl(latest.getObjectName(), PRESIGN_EXPIRY_SECONDS));
        vo.setParseResult(parseResult);
        return vo;
    }

    @Override
    public List<ResumeVersionVO> listVersions(Long resumeId, Long userId) {
        checkOwner(resumeId, userId);
        List<ResumeVersion> versions = resumeVersionMapper.selectList(
                new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resumeId)
                        .orderByDesc(ResumeVersion::getVersion)
        );
        return versions.stream().map(this::toVersionVO).toList();
    }

    @Override
    public List<ResumeKeyword> listKeywords(Long resumeId, Long userId) {
        checkOwner(resumeId, userId);
        return resumeKeywordMapper.selectList(
                new LambdaQueryWrapper<ResumeKeyword>()
                        .eq(ResumeKeyword::getResumeId, resumeId)
                        .orderByDesc(ResumeKeyword::getWeight)
        );
    }

    @Override
    public String getVersionPresignedUrl(Long versionId, Long userId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        if (version == null || !version.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return minioUtil.getPresignedUrl(version.getObjectName(), PRESIGN_EXPIRY_SECONDS);
    }

    @Override
    @Transactional
    public void saveKeywords(Long resumeId, Long userId, List<String> keywords) {
        resumeKeywordMapper.delete(
                new LambdaQueryWrapper<ResumeKeyword>()
                        .eq(ResumeKeyword::getResumeId, resumeId)
        );
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        for (String keyword : keywords) {
            ResumeKeyword item = new ResumeKeyword();
            item.setResumeId(resumeId);
            item.setUserId(userId);
            item.setKeyword(keyword);
            item.setType("SKILL");
            item.setWeight(BigDecimal.ONE);
            resumeKeywordMapper.insert(item);
        }
    }

    /** 把 Python AI 解析结果转换为本地 ResumeParseResult，用于写库。 */
    private ResumeParseResult toLocalParseResult(ResumeAiParseResult ai) {
        ResumeParseResult result = new ResumeParseResult();
        result.setName(ai.getName());
        result.setPhone(ai.getPhone());
        result.setEmail(ai.getEmail());
        result.setEducationList(ai.getEducation() == null ? List.of() : ai.getEducation());
        result.setSkills(ai.getSkills() == null ? List.of() : ai.getSkills());
        result.setProjects(ai.getProjects() == null ? List.of() : ai.getProjects());
        result.setKeywords(ai.getKeywords() == null ? List.of() : ai.getKeywords());
        return result;
    }

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPE.contains(contentType.toLowerCase())) {
            throw new BusinessException(400, "不支持的文件类型");
        }
    }

    private void checkOwner(Long resumeId, Long userId) {
        Resume resume = getById(resumeId);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private ResumeVersionVO toVersionVO(ResumeVersion v) {
        ResumeVersionVO vo = new ResumeVersionVO();
        vo.setVersionId(v.getId());
        vo.setVersion(v.getVersion());
        vo.setFileName(v.getFileName());
        vo.setFileSize(v.getFileSize());
        vo.setContentType(v.getContentType());
        vo.setFileType(v.getFileType());
        vo.setStatus(v.getStatus());
        vo.setCreatedAt(v.getCreatedAt());
        vo.setPresignedUrl(minioUtil.getPresignedUrl(v.getObjectName(), PRESIGN_EXPIRY_SECONDS));
        return vo;
    }

    private ResumeParseResult buildParseResult(Resume resume) {
        ResumeParseResult result = new ResumeParseResult();
        result.setName(resume.getParsedName());
        result.setPhone(resume.getParsedPhone());
        result.setEmail(resume.getParsedEmail());
        result.setEducationList(fromJson(resume.getEducationSummary()));
        result.setSkills(fromJson(resume.getSkillSummary()));
        result.setProjects(fromJson(resume.getProjectSummary()));
        result.setKeywords(resumeKeywordMapper.selectList(
                        new LambdaQueryWrapper<ResumeKeyword>()
                                .eq(ResumeKeyword::getResumeId, resume.getId()))
                .stream().map(ResumeKeyword::getKeyword).toList());
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(400, "解析结果序列化失败");
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}

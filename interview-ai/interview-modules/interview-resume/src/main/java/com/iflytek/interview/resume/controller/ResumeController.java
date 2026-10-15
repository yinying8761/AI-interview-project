package com.iflytek.interview.resume.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.resume.entity.ResumeKeyword;
import com.iflytek.interview.resume.service.ResumeService;
import com.iflytek.interview.resume.vo.ResumeVO;
import com.iflytek.interview.resume.vo.ResumeVersionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @PostMapping("/upload")
    public Result<ResumeVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(resumeService.uploadAndParse(userId, file));
    }

    @GetMapping("/current")
    public Result<ResumeVO> current() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(resumeService.getCurrent(userId));
    }

    @GetMapping("/{resumeId}/versions")
    public Result<List<ResumeVersionVO>> versions(@PathVariable Long resumeId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(resumeService.listVersions(resumeId, userId));
    }

    @GetMapping("/{resumeId}/keywords")
    public Result<List<ResumeKeyword>> keywords(@PathVariable Long resumeId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(resumeService.listKeywords(resumeId, userId));
    }

    @GetMapping("/versions/{versionId}/url")
    public Result<Map<String, String>> fileUrl(@PathVariable Long versionId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(Map.of("url", resumeService.getVersionPresignedUrl(versionId, userId)));
    }
}

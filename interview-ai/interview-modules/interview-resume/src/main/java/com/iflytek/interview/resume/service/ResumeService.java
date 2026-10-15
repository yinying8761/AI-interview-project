package com.iflytek.interview.resume.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.resume.entity.Resume;
import com.iflytek.interview.resume.entity.ResumeKeyword;
import com.iflytek.interview.resume.vo.ResumeVO;
import com.iflytek.interview.resume.vo.ResumeVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ResumeService extends IService<Resume> {
    ResumeVO uploadAndParse(Long userId, MultipartFile file) throws IOException;
    ResumeVO getCurrent(Long userId);
    List<ResumeVersionVO> listVersions(Long resumeId, Long userId);
    List<ResumeKeyword> listKeywords(Long resumeId, Long userId);
    String getVersionPresignedUrl(Long versionId, Long userId);
    void saveKeywords(Long resumeId, Long userId, List<String> keywords);
}

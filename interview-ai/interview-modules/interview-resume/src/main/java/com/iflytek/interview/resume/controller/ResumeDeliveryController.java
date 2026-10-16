package com.iflytek.interview.resume.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.resume.dto.SendResumeDTO;
import com.iflytek.interview.resume.service.ResumeDeliveryService;
import com.iflytek.interview.resume.vo.InterviewerVO;
import com.iflytek.interview.resume.vo.ResumeDeliveryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeDeliveryController {

    @Autowired
    private ResumeDeliveryService resumeDeliveryService;

    /** 列出全部在职面试官，供候选人选择接收人 */
    @GetMapping("/interviewers")
    public Result<List<InterviewerVO>> interviewers() {
        return Result.success(resumeDeliveryService.listInterviewers());
    }

    /** 候选人把简历发送给面试官 */
    @PostMapping("/{resumeId}/send")
    public Result<ResumeDeliveryVO> send(@PathVariable Long resumeId,
                                         @RequestBody SendResumeDTO dto) {
        return Result.success(resumeDeliveryService.send(
                SecurityUtil.getCurrentUserId(), resumeId, dto));
    }

    /** 候选人查看已发送记录 */
    @GetMapping("/sent")
    public Result<List<ResumeDeliveryVO>> sent() {
        return Result.success(resumeDeliveryService.listSent(SecurityUtil.getCurrentUserId()));
    }

    /** 面试官查看收到的简历列表 */
    @GetMapping("/received")
    public Result<List<ResumeDeliveryVO>> received() {
        return Result.success(resumeDeliveryService.listReceived(SecurityUtil.getCurrentUserId()));
    }

    /** 面试官查看某份收到简历的详情 */
    @GetMapping("/received/{deliveryId}")
    public Result<ResumeDeliveryVO> receivedDetail(@PathVariable Long deliveryId) {
        return Result.success(resumeDeliveryService.getReceivedDetail(
                deliveryId, SecurityUtil.getCurrentUserId()));
    }
}

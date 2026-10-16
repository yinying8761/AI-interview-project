package com.iflytek.interview.resume.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.resume.dto.SendResumeDTO;
import com.iflytek.interview.resume.entity.ResumeDelivery;
import com.iflytek.interview.resume.vo.InterviewerVO;
import com.iflytek.interview.resume.vo.ResumeDeliveryVO;

import java.util.List;

public interface ResumeDeliveryService extends IService<ResumeDelivery> {

    /** 列出全部在职面试官，供候选人选择接收人 */
    List<InterviewerVO> listInterviewers();

    /** 候选人把指定简历的最新已解析版本发送给面试官 */
    ResumeDeliveryVO send(Long candidateId, Long resumeId, SendResumeDTO dto);

    /** 候选人查看自己已发送的记录 */
    List<ResumeDeliveryVO> listSent(Long candidateId);

    /** 面试官查看自己收到的简历列表 */
    List<ResumeDeliveryVO> listReceived(Long interviewerId);

    /** 面试官查看某份收到简历的详情，并标记为已读 */
    ResumeDeliveryVO getReceivedDetail(Long deliveryId, Long interviewerId);
}

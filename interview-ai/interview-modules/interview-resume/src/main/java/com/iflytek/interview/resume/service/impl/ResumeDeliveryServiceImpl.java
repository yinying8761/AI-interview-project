package com.iflytek.interview.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.resume.dto.SendResumeDTO;
import com.iflytek.interview.resume.entity.Resume;
import com.iflytek.interview.resume.entity.ResumeDelivery;
import com.iflytek.interview.resume.entity.ResumeVersion;
import com.iflytek.interview.resume.mapper.ResumeDeliveryMapper;
import com.iflytek.interview.resume.mapper.ResumeMapper;
import com.iflytek.interview.resume.mapper.ResumeVersionMapper;
import com.iflytek.interview.resume.service.ResumeDeliveryService;
import com.iflytek.interview.resume.service.ResumeService;
import com.iflytek.interview.resume.vo.InterviewerVO;
import com.iflytek.interview.resume.vo.ResumeDeliveryVO;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.service.RoleService;
import com.iflytek.interview.system.service.UserRoleService;
import com.iflytek.interview.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeDeliveryServiceImpl extends ServiceImpl<ResumeDeliveryMapper, ResumeDelivery>
        implements ResumeDeliveryService {

    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private ResumeVersionMapper resumeVersionMapper;
    @Autowired
    private ResumeService resumeService;
    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UserRoleService userRoleService;

    @Override
    public List<InterviewerVO> listInterviewers() {
        List<Long> interviewerIds = listUserIdsByRole("interviewer");
        if (interviewerIds.isEmpty()) {
            return List.of();
        }
        return userService.listByIds(interviewerIds).stream()
                .filter(u -> u.getStatus() == null || u.getStatus() == 1)
                .map(u -> {
                    InterviewerVO vo = new InterviewerVO();
                    vo.setId(u.getId());
                    vo.setUsername(u.getUsername());
                    vo.setNickname(u.getNickname());
                    return vo;
                })
                .toList();
    }

    @Override
    @Transactional
    public ResumeDeliveryVO send(Long candidateId, Long resumeId, SendResumeDTO dto) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !resume.getUserId().equals(candidateId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ResumeVersion latest = resumeVersionMapper.selectOne(
                new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resumeId)
                        .orderByDesc(ResumeVersion::getVersion)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            throw new BusinessException(400, "简历还没有可发送的版本");
        }
        if (dto.getInterviewerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        validateInterviewer(dto.getInterviewerId());

        ResumeDelivery delivery = new ResumeDelivery();
        delivery.setResumeId(resumeId);
        delivery.setVersionId(latest.getId());
        delivery.setCandidateId(candidateId);
        delivery.setInterviewerId(dto.getInterviewerId());
        delivery.setMessage(dto.getMessage());
        delivery.setStatus("SENT");
        save(delivery);

        return toVO(delivery);
    }

    @Override
    public List<ResumeDeliveryVO> listSent(Long candidateId) {
        return list(new LambdaQueryWrapper<ResumeDelivery>()
                        .eq(ResumeDelivery::getCandidateId, candidateId)
                        .orderByDesc(ResumeDelivery::getCreatedAt))
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<ResumeDeliveryVO> listReceived(Long interviewerId) {
        return list(new LambdaQueryWrapper<ResumeDelivery>()
                        .eq(ResumeDelivery::getInterviewerId, interviewerId)
                        .orderByDesc(ResumeDelivery::getCreatedAt))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public ResumeDeliveryVO getReceivedDetail(Long deliveryId, Long interviewerId) {
        ResumeDelivery delivery = getById(deliveryId);
        if (delivery == null || !interviewerId.equals(delivery.getInterviewerId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ResumeDeliveryVO vo = toVO(delivery);
        vo.setResume(resumeService.getDetail(delivery.getResumeId()));
        if (!"READ".equals(delivery.getStatus())) {
            delivery.setStatus("READ");
            updateById(delivery);
            vo.setStatus("READ");
        }
        return vo;
    }

    private void validateInterviewer(Long interviewerId) {
        User user = userService.getById(interviewerId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            throw new BusinessException(400, "面试官不存在或已停用");
        }
        if (!isUserRole(interviewerId, "interviewer")) {
            throw new BusinessException(400, "接收人不是面试官");
        }
    }

    private ResumeDeliveryVO toVO(ResumeDelivery d) {
        ResumeDeliveryVO vo = new ResumeDeliveryVO();
        vo.setDeliveryId(d.getId());
        vo.setResumeId(d.getResumeId());
        vo.setVersionId(d.getVersionId());
        vo.setCandidateId(d.getCandidateId());
        vo.setInterviewerId(d.getInterviewerId());
        vo.setMessage(d.getMessage());
        vo.setStatus(d.getStatus());
        vo.setSentAt(d.getCreatedAt());

        ResumeVersion version = d.getVersionId() == null ? null : resumeVersionMapper.selectById(d.getVersionId());
        vo.setFileName(version == null ? null : version.getFileName());

        User candidate = d.getCandidateId() == null ? null : userService.getById(d.getCandidateId());
        User interviewer = d.getInterviewerId() == null ? null : userService.getById(d.getInterviewerId());
        vo.setCandidateName(candidate == null ? null : displayName(candidate));
        vo.setInterviewerName(interviewer == null ? null : displayName(interviewer));
        return vo;
    }

    private String displayName(User user) {
        return (user.getNickname() == null || user.getNickname().isBlank())
                ? user.getUsername() : user.getNickname();
    }

    private boolean isUserRole(Long userId, String roleCode) {
        Role role = roleService.getOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode), false);
        if (role == null) {
            return false;
        }
        return userRoleService.count(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId())) > 0;
    }

    private List<Long> listUserIdsByRole(String roleCode) {
        Role role = roleService.getOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode), false);
        if (role == null) {
            return List.of();
        }
        return userRoleService.list(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, role.getId()))
                .stream().map(UserRole::getUserId).distinct().toList();
    }
}

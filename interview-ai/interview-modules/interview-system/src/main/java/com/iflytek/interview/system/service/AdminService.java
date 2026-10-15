package com.iflytek.interview.system.service;

import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.dto.AdminUpdateUserDTO;
import com.iflytek.interview.system.dto.AdminUserQueryDTO;
import com.iflytek.interview.system.dto.ResetPasswordDTO;
import com.iflytek.interview.system.dto.UserStatusDTO;
import com.iflytek.interview.system.vo.UserVO;
import com.iflytek.interview.common.response.PageResult;

import java.util.List;

public interface AdminService {

    PageResult<UserVO> listUsers(AdminUserQueryDTO query);

    List<Role> listRoles();

    void assignRoles(Long userId, List<Long> roleIds);

    void resetPassword(Long userId, ResetPasswordDTO dto);

    UserVO updateUser(Long userId, AdminUpdateUserDTO dto);

    void updateStatus(Long userId, UserStatusDTO dto);
}

package com.iflytek.interview.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.system.dto.ChangePasswordDTO;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.dto.UpdateProfileDTO;
import com.iflytek.interview.system.entity.User;

public interface UserService extends IService<User> {

    boolean existsByUsername(String username);

    User getByUsername(String username);

    User register(RegisterDTO dto);

    void updateProfile(Long userId, UpdateProfileDTO dto);

    void changePassword(Long userId, ChangePasswordDTO dto);
}

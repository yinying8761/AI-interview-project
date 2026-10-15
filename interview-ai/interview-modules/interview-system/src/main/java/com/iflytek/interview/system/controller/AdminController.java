package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.response.PageResult;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.system.dto.AdminUpdateUserDTO;
import com.iflytek.interview.system.dto.AdminUserQueryDTO;
import com.iflytek.interview.system.dto.ResetPasswordDTO;
import com.iflytek.interview.system.dto.UserStatusDTO;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.service.AdminService;
import com.iflytek.interview.system.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/users")
    public Result<PageResult<UserVO>> listUsers(AdminUserQueryDTO query) {
        return Result.success(adminService.listUsers(query));
    }

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        return Result.success(adminService.listRoles());
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/users/{id}/role")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @RequestBody List<Long> roleIds) {
        adminService.assignRoles(id, roleIds);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordDTO dto) {
        adminService.resetPassword(id, dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/users/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id,
                                     @Valid @RequestBody AdminUpdateUserDTO dto) {
        return Result.success(adminService.updateUser(id, dto));
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusDTO dto) {
        adminService.updateStatus(id, dto);
        return Result.success();
    }
}

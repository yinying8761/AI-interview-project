package com.iflytek.interview.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@TableName("sys_user") // 标识对应数据库里的表名
public class User {

    @TableId(type = IdType.AUTO) // 标识表中的主键字段
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String nickname;

    private String avatar;

    /**
     * 角色标识。sys_user 表无此列，角色由 user_role + role 关联表维护，
     * 故标记为非数据库字段，仅用于序列化透出。
     */
    @TableField(exist = false)
    private String role;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
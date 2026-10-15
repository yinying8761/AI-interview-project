package com.iflytek.interview.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.interview.system.entity.User;

public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，自动拥有 CRUD 方法：
    // selectById、selectList、insert、updateById、deleteById 等
}

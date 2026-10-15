package com.iflytek.interview.config;

import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时初始化：把 interview_ai.sql 里明文密码的用户重置为 BCrypt。
 * 约定：admin -> admin123，其它用户保留原密码。
 */
@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        List<User> users = userMapper.selectList(null);
        for (User user : users) {
            if (user.getPassword() != null && !user.getPassword().startsWith("$2")) {
                String raw = "admin".equalsIgnoreCase(user.getUsername()) ? "admin123" : user.getPassword();
                user.setPassword(passwordEncoder.encode(raw));
                userMapper.updateById(user);
                log.info("[初始化] 用户 {} 密码已重置为 BCrypt", user.getUsername());
            }
        }
    }
}

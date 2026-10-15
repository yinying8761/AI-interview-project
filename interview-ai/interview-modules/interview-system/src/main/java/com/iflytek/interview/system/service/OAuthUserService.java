package com.iflytek.interview.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.entity.OAuthBinding;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.mapper.OAuthBindingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class OAuthUserService {

    @Autowired
    private OAuthBindingMapper bindingMapper;

    @Autowired
    private UserService userService;

    public OAuthBinding getBinding(String provider, Long userId) {
        return bindingMapper.selectOne(
                new LambdaQueryWrapper<OAuthBinding>()
                        .eq(OAuthBinding::getProvider, provider)
                        .eq(OAuthBinding::getUserId, userId));
    }

    @Transactional
    public User findOrCreate(String provider, OAuth2User oAuth2User) {

        Map<String, Object> attributes = oAuth2User.getAttributes();

        String providerUserId = text(attributes, "id");
        if (providerUserId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        OAuthBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<OAuthBinding>()
                        .eq(OAuthBinding::getProvider, provider)
                        .eq(OAuthBinding::getProviderUserId, providerUserId));

        if (binding != null) {
            User boundUser = userService.getById(binding.getUserId());
            if (boundUser == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            return boundUser;
        }

        String providerUsername = text(attributes, "login");
        String nickname = text(attributes, "name");
        String avatarUrl = text(attributes, "avatar_url");
        String email = text(attributes, "email");

        if (nickname.isBlank()) {
            nickname = providerUsername;
        }
        if (email.isBlank()) {
            email = providerUserId + "@oauth.local";
        }

        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(provider + "_" + providerUserId);
        registerDTO.setPassword(UUID.randomUUID().toString());
        registerDTO.setEmail(email);

        User user = userService.register(registerDTO);

        if (!nickname.isBlank()) {
            user.setNickname(nickname);
        }
        if (!avatarUrl.isBlank()) {
            user.setAvatar(avatarUrl);
        }
        userService.updateById(user);

        binding = new OAuthBinding();
        binding.setUserId(user.getId());
        binding.setProvider(provider);
        binding.setProviderUserId(providerUserId);
        binding.setProviderUsername(providerUsername);
        binding.setAvatarUrl(avatarUrl);
        bindingMapper.insert(binding);

        return user;
    }

    private String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @Transactional
    public void bindExistingUser(String provider, OAuth2User oAuth2User, Long userId) {

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerUserId = text(attributes, "id");
        if (providerUserId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        OAuthBinding providerBinding = bindingMapper.selectOne(
                new LambdaQueryWrapper<OAuthBinding>()
                        .eq(OAuthBinding::getProvider, provider)
                        .eq(OAuthBinding::getProviderUserId, providerUserId));

        if (providerBinding != null
                && !providerBinding.getUserId().equals(userId)) {
            throw new BusinessException(400, "该Gitee账号已绑定其他用户");
        }

        OAuthBinding userBinding = bindingMapper.selectOne(
                new LambdaQueryWrapper<OAuthBinding>()
                        .eq(OAuthBinding::getUserId, userId)
                        .eq(OAuthBinding::getProvider, provider));

        if (userBinding != null) {
            throw new BusinessException(400, "当前用户已绑定Gitee账号");
        }

        OAuthBinding binding = new OAuthBinding();
        binding.setUserId(userId);
        binding.setProvider(provider);
        binding.setProviderUserId(providerUserId);
        binding.setProviderUsername(text(attributes, "login"));
        binding.setAvatarUrl(text(attributes, "avatar_url"));
        bindingMapper.insert(binding);
    }
}

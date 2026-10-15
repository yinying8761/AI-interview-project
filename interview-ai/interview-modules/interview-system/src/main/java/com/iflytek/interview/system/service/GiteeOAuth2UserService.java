package com.iflytek.interview.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component("giteeOAuth2UserService")
public class GiteeOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITEE_USER_INFO_URI =
            "https://gitee.com/api/v5/user?access_token={accessToken}";

    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            Map<String, Object> attributes = restClient.get()
                    .uri(GITEE_USER_INFO_URI, accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (attributes == null || !attributes.containsKey("id")) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_gitee_user_info", "Gitee用户信息缺失", null));
            }

            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new OAuth2UserAuthority(attributes, "id"));

            return new DefaultOAuth2User(authorities, attributes, "id");
        } catch (Exception exception) {
            log.error("获取Gitee用户信息失败", exception);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "invalid_gitee_user_info",
                            "获取Gitee用户信息失败",
                            null),
                    exception);
        }
    }
}

package org.example.datn.security.oauth2;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.User;
import org.example.datn.domain.enums.Role;
import org.example.datn.Repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a Google profile to a local user (creating one as CUSTOMER on first
 * login). Used only when an OAuth2 client registration is configured.
 */
@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String googleId = oAuth2User.getAttribute("sub");

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> email == null ? java.util.Optional.empty() : userRepository.findByEmail(email))
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName(name != null ? name : email)
                        .email(email)
                        .googleId(googleId)
                        .role(Role.CUSTOMER)
                        .status(true)
                        .build()));

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            userRepository.save(user);
        }

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("userId", user.getUserId());
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes, "email");
    }
}

package com.project.FitLink.auth;


import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email).orElseThrow(()
                -> new UsernameNotFoundException("User not found for this email : " + email));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRole().getRoleCode().name()))
                .collect(Collectors.toList());

        if (authorities.isEmpty()) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return FitLinkUserDetails.builder()
                .publicId(user.getPublicId())
                .username(user.getUserName())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}

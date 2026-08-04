package com.project.FitLink.auth;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Builder
public class FitLinkUserDetails implements UserDetails {

    private final Long id;
    private final UUID publicId;
    private final String username;
    private final String email;
    private final String password;
    private final int tokenVersion;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}



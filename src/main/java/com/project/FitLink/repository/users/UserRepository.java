package com.project.FitLink.repository.users;

import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.utils.enums.auth.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity,Long> {
    //TODO: Use entity graph in queries that u will fetch related entities in it
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByPublicId(UUID publicId);
    Optional<UserEntity> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByEmail(String email);
}

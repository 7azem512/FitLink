package com.project.FitLink.repository.users;

import com.project.FitLink.entities.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByPublicId(UUID publicId);
    boolean existsByEmail(String email);
}

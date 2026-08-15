package com.project.FitLink.repository.roles;

import com.project.FitLink.entities.roles.GymProfile;
import com.project.FitLink.entities.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GymProfileRepository extends JpaRepository<GymProfile, UUID> {
    Optional<GymProfile> findByUser(UserEntity user);
    Optional<GymProfile> findByUser_PublicId(UUID publicId);
    boolean existsByUser(UserEntity user);
}

package com.project.FitLink.repository.roles;

import com.project.FitLink.entities.roles.CoachProfile;
import com.project.FitLink.entities.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, UUID> {
    Optional<CoachProfile> findByUser(UserEntity user);
    Optional<CoachProfile> findByUser_PublicId(UUID publicId);
    boolean existsByUser(UserEntity user);
}

package com.project.FitLink.repository.roles;

import com.project.FitLink.entities.roles.TraineeProfile;
import com.project.FitLink.entities.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TraineeProfileRepository extends JpaRepository<TraineeProfile, UUID> {
    Optional<TraineeProfile> findByUser(UserEntity user);
    Optional<TraineeProfile> findByUser_PublicId(UUID publicId);
    boolean existsByUser(UserEntity user);
}

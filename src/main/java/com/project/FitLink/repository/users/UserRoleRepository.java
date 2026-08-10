package com.project.FitLink.repository.users;

import com.project.FitLink.entities.users.UserEntity;
import com.project.FitLink.entities.users.UserRole;
import com.project.FitLink.utils.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUser(UserEntity user);
    Optional<UserRole> findByUserAndRole_RoleCode(UserEntity user, Roles roleCode);
    boolean existsByUserAndRole_RoleCode(UserEntity user, Roles roleCode);
}

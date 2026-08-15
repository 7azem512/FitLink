package com.project.FitLink.repository.users;

import com.project.FitLink.entities.users.Role;
import com.project.FitLink.utils.enums.user.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleCode(Roles roleCode);
    boolean existsByRoleCode(Roles roleCode);
}

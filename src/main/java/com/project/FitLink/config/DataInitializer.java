package com.project.FitLink.config;

import com.project.FitLink.entities.users.Role;
import com.project.FitLink.repository.users.RoleRepository;
import com.project.FitLink.utils.enums.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (Roles roleCode : Roles.values()) {
            if (!roleRepository.existsByRoleCode(roleCode)) {
                Role role = new Role();
                role.setRoleCode(roleCode);
                roleRepository.save(role);
                log.info("Seeded role: {}", roleCode);
            }
        }
    }
}

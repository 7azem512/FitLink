package com.project.FitLink.entities.users;

import com.project.FitLink.auditing.AuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_role",
                        columnNames = {"user_id", "role_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}
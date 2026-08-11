package com.project.FitLink.entities.users;

import com.project.FitLink.auditing.AuditEntity;
import com.project.FitLink.utils.enums.AuthProvider;
import com.project.FitLink.utils.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_app_user_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uq_app_user_email",
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = "uq_app_user_phone",
                        columnNames = "phone"
                ),
                @UniqueConstraint(
                        name = "uq_app_user_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@SuperBuilder
public class UserEntity extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(
            name = "public_id",
            nullable = false,
            updatable = false
    )
    private UUID publicId;
    @Column(
            name = "email",
            nullable = false,
            length = 255
    )
    private String email;
    @Column(
            name = "phone",
            length = 20
    )
    private String phone;
    @Column(
            name = "user_name",
            nullable = false,
            length = 50
    )
    @Size(min = 3, max = 50)
    private String userName;
    @Column(
            name = "password_hash",
            nullable = false
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private UserStatus status;

    @Column(
            name = "email_verified",
            nullable = false
    )
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            length = 20
    )
    private AuthProvider provider;

    @Column(
            name = "provider_id",
            length = 255
    )
    private String providerId;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserRole> roles;

    @PrePersist
    private void initialize() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (status == null) {
            status = UserStatus.PENDING;
        }
    }
}

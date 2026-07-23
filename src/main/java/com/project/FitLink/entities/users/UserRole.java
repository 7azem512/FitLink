package com.project.FitLink.entities.users;

import com.project.FitLink.auditing.AuditEntity;
import com.project.FitLink.utils.enums.Roles;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "user_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_role",
                        columnNames = {"user_id", "role_code"}
                )
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserRole extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role_user")
    )
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role_code",
            nullable = false,
            length = 30
    )
    private Roles roleCode;

}

package com.project.FitLink.repository;

import com.project.FitLink.entities.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    Optional<TokenBlacklist> findByToken(String token);

    boolean existsByToken(String token);

    @Query("DELETE FROM TokenBlacklist t WHERE t.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);

    @Query("SELECT t FROM TokenBlacklist t WHERE t.userId = :userId AND t.expiryDate > CURRENT_TIMESTAMP")
    java.util.List<TokenBlacklist> findActiveBlacklistsByUserId(Long userId);
}

package com.hrgenius.auth.repository;

import com.hrgenius.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * Bound as parameters rather than true/false literals: the column is NUMBER(1), and literal
     * booleans are not comparable with it on Oracle or H2.
     */
    @Modifying
    @Query("update RefreshToken r set r.revoked = :yes where r.userId = :userId and r.revoked = :no")
    void revokeAll(@Param("userId") Long userId, @Param("yes") boolean yes, @Param("no") boolean no);

    default void revokeAllForUser(Long userId) {
        revokeAll(userId, true, false);
    }
}

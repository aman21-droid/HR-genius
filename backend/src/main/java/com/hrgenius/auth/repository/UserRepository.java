package com.hrgenius.auth.repository;

import com.hrgenius.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    /** Active accounts linked to an employee that hold the given permission (via any role). */
    @Query("""
            select distinct u from User u
              join u.roles r
              join r.permissions p
            where p.code = :perm
              and u.employeeId is not null
              and u.status = com.hrgenius.auth.entity.User.UserStatus.ACTIVE
            """)
    List<User> findApproversByPermission(@Param("perm") String perm);
}

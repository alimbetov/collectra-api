package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCodeAndTenantIdIsNull(String code);
    Optional<Role> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    @Query("select role from Role role where role.tenantId = :tenantId or (role.tenantId is null and role.scopeType = 'TENANT')")
    List<Role> findAvailableTenantRoles(@Param("tenantId") UUID tenantId);

    @Query(
            value = """
                    select distinct p.code
                      from membership_roles mr
                      join roles r on r.id = mr.role_id
                      join role_permissions rp on rp.role_id = r.id
                      join permissions p on p.id = rp.permission_id
                     where mr.membership_id = :membershipId
                    """,
            nativeQuery = true)
    List<String> findPermissionCodes(@Param("membershipId") UUID membershipId);

    @Query(
            value = """
                    select distinct r.code
                      from membership_roles mr
                      join roles r on r.id = mr.role_id
                     where mr.membership_id = :membershipId
                    """,
            nativeQuery = true)
    List<String> findRoleCodes(@Param("membershipId") UUID membershipId);
}

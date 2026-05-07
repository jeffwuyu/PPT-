package com.pptagent.api.auth;

import com.pptagent.domain.auth.PermissionEntity;
import com.pptagent.domain.auth.RoleEntity;
import com.pptagent.domain.auth.RolePermissionEntity;
import com.pptagent.domain.auth.UserEntity;
import com.pptagent.domain.auth.UserRoleEntity;
import com.pptagent.domain.auth.repository.PermissionRepository;
import com.pptagent.domain.auth.repository.RolePermissionRepository;
import com.pptagent.domain.auth.repository.RoleRepository;
import com.pptagent.domain.auth.repository.UserLevelRepository;
import com.pptagent.domain.auth.repository.UserRepository;
import com.pptagent.domain.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAccessGraphService {

    private final UserRepository userRepository;
    private final UserLevelRepository userLevelRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public UserAccessGraphService(
            UserRepository userRepository,
            UserLevelRepository userLevelRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository
    ) {
        this.userRepository = userRepository;
        this.userLevelRepository = userLevelRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::attachAccessGraph);
    }

    public Optional<UserEntity> findById(Long id) {
        return Optional.ofNullable(userRepository.selectById(id)).map(this::attachAccessGraph);
    }

    private UserEntity attachAccessGraph(UserEntity user) {
        user.setLevel(userLevelRepository.selectById(user.getLevelId()));

        List<UserRoleEntity> userRoles = userRoleRepository.findByUserId(user.getId());
        List<Long> roleIds = userRoles.stream()
                .map(UserRoleEntity::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            user.setRoles(Set.of());
            return user;
        }

        List<RoleEntity> roles = roleRepository.selectBatchIds(roleIds);
        Map<Long, RoleEntity> roleById = roles.stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));

        List<RolePermissionEntity> rolePermissions = rolePermissionRepository.findByRoleIds(roleIds);
        List<Long> permissionIds = rolePermissions.stream()
                .map(RolePermissionEntity::getPermissionId)
                .distinct()
                .toList();
        Map<Long, PermissionEntity> permissionById = permissionIds.isEmpty()
                ? Map.of()
                : permissionRepository.selectBatchIds(permissionIds).stream()
                        .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));

        Map<Long, Set<PermissionEntity>> permissionsByRoleId = rolePermissions.stream()
                .collect(Collectors.groupingBy(
                        RolePermissionEntity::getRoleId,
                        Collectors.mapping(rolePermission -> permissionById.get(rolePermission.getPermissionId()), Collectors.toSet())
                ));

        roles.forEach(role -> role.setPermissions(permissionsByRoleId.getOrDefault(role.getId(), Set.of()).stream()
                .filter(permission -> permission != null)
                .collect(Collectors.toSet())));
        user.setRoles(new HashSet<>(roleById.values()));
        return user;
    }
}

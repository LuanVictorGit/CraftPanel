package br.com.craftpanel.users;

import br.com.craftpanel.auth.PasswordHasher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public List<User> listUsers() {
        return userRepo.findAll();
    }

    public Optional<User> getUser(String id) {
        return userRepo.findById(id);
    }

    public User createUser(String username, String password, String displayName, String roleId) {
        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (userRepo.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepo.findRoleById(roleId).isEmpty()) {
            throw new IllegalArgumentException("Specified role does not exist");
        }

        String id = UUID.randomUUID().toString();
        String hash = PasswordHasher.hash(password);
        Instant now = Instant.now();
        String name = (displayName != null && !displayName.isBlank()) ? displayName.trim() : username.trim();

        User user = new User(id, username.trim(), name, roleId, now, now);
        userRepo.create(user, hash);
        return user;
    }

    public User updateUser(String id, String displayName, String roleId) {
        User existing = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (userRepo.findRoleById(roleId).isEmpty()) {
            throw new IllegalArgumentException("Role not found");
        }

        if ("admin".equals(existing.roleId()) && !"admin".equals(roleId)) {
            long adminCount = userRepo.findAll().stream().filter(u -> "admin".equals(u.roleId())).count();
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot demote the last administrator");
            }
        }

        String name = (displayName != null && !displayName.isBlank()) ? displayName.trim() : existing.displayName();
        User updated = new User(existing.id(), existing.username(), name, roleId, existing.createdAt(), Instant.now());
        userRepo.update(updated);
        return updated;
    }

    public void resetPassword(String id, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String hash = PasswordHasher.hash(newPassword);
        userRepo.updatePassword(id, hash);
    }

    public void deleteUser(String id) {
        User user = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("admin".equals(user.roleId())) {
            long adminCount = userRepo.findAll().stream().filter(u -> "admin".equals(u.roleId())).count();
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot delete the only administrator");
            }
        }
        userRepo.delete(id);
    }

    public List<Role> listRoles() {
        return userRepo.findAllRoles();
    }

    public Optional<Role> getRole(String roleId) {
        return userRepo.findRoleById(roleId);
    }

    public Role createRole(String name, String description, List<String> permissions) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be empty");
        }
        String id = name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (userRepo.findRoleById(id).isPresent()) {
            id = id + "_" + System.currentTimeMillis();
        }
        Role role = new Role(id, name.trim(), description, false, permissions);
        userRepo.createRole(role);
        return role;
    }

    public Role updateRole(String id, String name, String description, List<String> permissions) {
        Role existing = userRepo.findRoleById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (existing.isSystem()) {
            throw new IllegalArgumentException("System roles cannot be modified");
        }
        Role updated = new Role(existing.id(), name.trim(), description, false, permissions);
        userRepo.updateRole(updated);
        return updated;
    }

    public void deleteRole(String id) {
        Role existing = userRepo.findRoleById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (existing.isSystem()) {
            throw new IllegalArgumentException("System roles cannot be deleted");
        }
        userRepo.deleteRole(id);
    }

    public Set<String> getServerPermissions(String userId, String serverId) {
        return userRepo.getUserServerPermissions(userId, serverId);
    }

    public Set<String> getEffectiveServerPermissions(String userId, String serverId) {
        Set<String> result = new java.util.HashSet<>(userRepo.getUserServerPermissions(userId, serverId));
        Optional<User> userOpt = userRepo.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Optional<Role> roleOpt = userRepo.findRoleById(user.roleId());
            if (roleOpt.isPresent()) {
                List<String> rolePerms = roleOpt.get().permissions();
                if (rolePerms.contains(Permission.ALL)) {
                    result.add(Permission.ALL);
                } else {
                    result.addAll(rolePerms);
                }
            }
        }
        return result;
    }

    public void setServerPermissions(String userId, String serverId, List<String> permissions) {
        userRepo.setUserServerPermissions(userId, serverId, permissions);
    }

    public boolean hasPermission(User user, String permission) {
        if (user == null) return false;
        Optional<Role> roleOpt = userRepo.findRoleById(user.roleId());
        if (roleOpt.isEmpty()) return false;
        List<String> perms = roleOpt.get().permissions();
        return perms.contains(Permission.ALL) || perms.contains(permission);
    }

    public boolean hasServerPermission(User user, String serverId, String permission) {
        if (user == null) return false;
        if (hasPermission(user, permission)) {
            return true;
        }
        Set<String> serverPerms = userRepo.getUserServerPermissions(user.id(), serverId);
        return serverPerms.contains(Permission.ALL) || serverPerms.contains(permission);
    }
}

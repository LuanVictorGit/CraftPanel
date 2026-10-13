package br.com.craftpanel.auth;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final UserRepository userRepo;
    private final PanelConfig config;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LoginAttempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, WsTicket> wsTickets = new ConcurrentHashMap<>();

    private record LoginAttempt(int count, Instant lockedUntil) {}
    private record WsTicket(String userId, Instant expiresAt) {}

    public AuthService(UserRepository userRepo, PanelConfig config) {
        this.userRepo = userRepo;
        this.config = config;
    }

    public boolean isSetupRequired() {
        return !userRepo.hasAnyUsers();
    }

    public synchronized User setupFirstAdmin(String username, String password, String displayName) {
        if (!isSetupRequired()) {
            throw new IllegalStateException("Initial setup has already been completed");
        }
        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        String id = UUID.randomUUID().toString();
        String hash = PasswordHasher.hash(password);
        Instant now = Instant.now();
        String name = (displayName != null && !displayName.isBlank()) ? displayName.trim() : username.trim();

        User user = new User(id, username.trim(), name, "admin", now, now);
        userRepo.create(user, hash);
        return user;
    }

    public Session login(String username, String password, String ip, String userAgent) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password are required");
        }

        checkLockout(ip);

        Optional<User> userOpt = userRepo.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            recordFailedAttempt(ip);
            throw new SecurityException("Invalid credentials");
        }

        User user = userOpt.get();
        Optional<String> hashOpt = userRepo.getPasswordHash(user.id());
        if (hashOpt.isEmpty() || !PasswordHasher.verify(password, hashOpt.get())) {
            recordFailedAttempt(ip);
            throw new SecurityException("Invalid credentials");
        }

        clearAttempts(ip);

        String token = generateSecureToken();
        int timeoutMinutes = config.security().sessionTimeoutMinutes();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(timeoutMinutes, ChronoUnit.MINUTES);

        Session session = new Session(
                token,
                user.id(),
                user.username(),
                user.displayName(),
                user.roleId(),
                ip,
                userAgent,
                expiresAt,
                now
        );

        userRepo.saveSession(session);
        return session;
    }

    public Optional<Session> validateSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepo.findSessionByToken(token);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            userRepo.deleteSession(token);
        }
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        Optional<String> currentHash = userRepo.getPasswordHash(userId);
        if (currentHash.isEmpty() || !PasswordHasher.verify(oldPassword, currentHash.get())) {
            throw new SecurityException("Current password does not match");
        }
        String newHash = PasswordHasher.hash(newPassword);
        userRepo.updatePassword(userId, newHash);
    }

    public String createWebSocketTicket(String userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);
        wsTickets.put(ticket, new WsTicket(userId, Instant.now().plus(30, ChronoUnit.SECONDS)));
        return ticket;
    }

    public Optional<String> validateWebSocketTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        WsTicket wsTicket = wsTickets.remove(ticket);
        if (wsTicket != null && Instant.now().isBefore(wsTicket.expiresAt())) {
            return Optional.of(wsTicket.userId());
        }
        return Optional.empty();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void checkLockout(String ip) {
        if (ip == null) return;
        LoginAttempt attempt = attempts.get(ip);
        if (attempt != null && attempt.lockedUntil() != null) {
            if (Instant.now().isBefore(attempt.lockedUntil())) {
                long minutesLeft = ChronoUnit.MINUTES.between(Instant.now(), attempt.lockedUntil()) + 1;
                throw new SecurityException("Too many failed attempts. Account locked for " + minutesLeft + " more minute(s).");
            } else {
                attempts.remove(ip);
            }
        }
    }

    private void recordFailedAttempt(String ip) {
        if (ip == null) return;
        int maxAttempts = config.security().maxFailedLoginAttempts();
        int lockoutMinutes = config.security().lockoutDurationMinutes();

        attempts.compute(ip, (k, v) -> {
            int current = (v == null) ? 1 : v.count() + 1;
            Instant lockedUntil = (current >= maxAttempts) ? Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES) : null;
            return new LoginAttempt(current, lockedUntil);
        });
    }

    private void clearAttempts(String ip) {
        if (ip != null) {
            attempts.remove(ip);
        }
    }
}

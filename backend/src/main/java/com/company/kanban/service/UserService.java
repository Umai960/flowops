package com.company.kanban.service;

import com.company.kanban.dto.CreateUserRequest;
import com.company.kanban.dto.UserResponse;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.User;
import com.company.kanban.entity.AccountStatus;
import com.company.kanban.dto.UpdateUserRequest;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.ActivationTokenRepository;
import com.company.kanban.repository.DailyWorkReportRepository;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivationTokenRepository activationTokenRepository;
    private final TaskRepository taskRepository;
    private final DailyWorkReportRepository dailyWorkReportRepository;
    private final NotificationRepository notificationRepository;

    public UserService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            ActivationTokenRepository activationTokenRepository,
            TaskRepository taskRepository,
            DailyWorkReportRepository dailyWorkReportRepository,
            NotificationRepository notificationRepository) {

        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.activationTokenRepository = activationTokenRepository;
        this.taskRepository = taskRepository;
        this.dailyWorkReportRepository = dailyWorkReportRepository;
        this.notificationRepository = notificationRepository;
    }

    // Optional so unit tests that build UserService directly do not need the chat module.
    private ChatService chatService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setChatService(ChatService chatService) { this.chatService = chatService; }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

        @Transactional(readOnly = true)
        public List<UserResponse> getAssignableUsers(User currentUser) {
                List<User> users;

                if (currentUser.getRole() == Role.ADMIN) {
                        users = userRepository.findByStatus(AccountStatus.ACTIVE);
                } else {
                        users = userRepository.findByDepartmentIdOrderByNameAsc(
                                        currentUser.getDepartment().getId()
                        ).stream().filter(user -> user.getStatus() == AccountStatus.ACTIVE).toList();
                }

                return users.stream()
                                .map(this::toResponse)
                                .toList();
        }

    @Transactional(readOnly = true)
    public List<UserResponse> getTaskAssignees(User currentUser) {
        List<User> users;

        if (currentUser.getRole() == Role.ADMIN) {
            users = userRepository.findByStatus(AccountStatus.ACTIVE);
        } else {
            users = userRepository.findByDepartmentIdOrderByNameAsc(currentUser.getDepartment().getId())
                    .stream()
                    .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                    .filter(user -> user.getRole() == Role.STAFF
                            || user.getId().equals(currentUser.getId()))
                    .toList();
        }

        return users.stream().map(this::toResponse).toList();
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );

        return toResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (request.departmentId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required");
        String email = requireLoginId(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username or email already exists"
            );
        }

        Department department =
                departmentRepository.findById(request.departmentId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Department not found"
                                )
                        );

        String password = requirePassword(request.password());

        Role role = request.role() == null ? Role.STAFF : request.role();
        User user = new User(
                requireText(request.name(), "Name"),
                email,
                passwordEncoder.encode(password),
                role,
                department
        );
        user.setStatus(AccountStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        log.info("ADMIN action: user created id={} role={} departmentId={}", savedUser.getId(), savedUser.getRole(), department.getId());
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse setPassword(Long id, String password) {
        User user = requireUser(id);
        user.setPassword(passwordEncoder.encode(requirePassword(password)));
        activationTokenRepository.deleteByUser(user);
        if (user.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setStatusBeforeDisabled(null);
        }
        log.info("ADMIN action: password replaced for user id={}", id);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        if (request.departmentId() == null || request.role() == null || request.status() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department, role, and status are required");
        User user = requireUser(id);
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        if ((user.getStatus() == AccountStatus.PENDING_ACTIVATION || user.getStatusBeforeDisabled() == AccountStatus.PENDING_ACTIVATION) && request.status() == AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pending accounts must set a password through their activation link");
        lockAndProtectLastAdmin(user, request.role(), request.status());
        Role oldRole = user.getRole(); Long oldDepartment = user.getDepartment().getId(); AccountStatus oldStatus = user.getStatus();
        user.setName(requireText(request.name(), "Name")); user.setDepartment(department); user.setRole(request.role());
        if (request.status() == AccountStatus.DISABLED && oldStatus != AccountStatus.DISABLED) user.setStatusBeforeDisabled(oldStatus);
        user.setStatus(request.status());
        if (oldRole != request.role()) log.info("ADMIN action: user role changed id={} from={} to={}", id, oldRole, request.role());
        if (!oldDepartment.equals(department.getId())) log.info("ADMIN action: user department changed id={} from={} to={}", id, oldDepartment, department.getId());
        if (oldStatus != request.status()) log.info("ADMIN action: user status changed id={} from={} to={}", id, oldStatus, request.status());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse disable(Long id) {
        User user = requireUser(id); lockAndProtectLastAdmin(user, user.getRole(), AccountStatus.DISABLED);
        if (user.getStatus() != AccountStatus.DISABLED) user.setStatusBeforeDisabled(user.getStatus());
        user.setStatus(AccountStatus.DISABLED); log.info("ADMIN action: account disabled id={}", id);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse reactivate(Long id) {
        User user = requireUser(id); user.setStatus(user.getStatusBeforeDisabled() == AccountStatus.PENDING_ACTIVATION ? AccountStatus.PENDING_ACTIVATION : AccountStatus.ACTIVE); user.setStatusBeforeDisabled(null);
        log.info("ADMIN action: account reactivated id={}", id); return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteDisabledUser(Long id) {
        User user = requireUser(id);
        if (user.getStatus() != AccountStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only disabled users can be deleted");
        }
        if (taskRepository.existsByAssigneeIdOrCreatedById(id, id)
                || dailyWorkReportRepository.existsByUserId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This user has task or report history and cannot be deleted; keep the account disabled instead"
            );
        }

        activationTokenRepository.deleteByUser(user);
        notificationRepository.deleteByRecipientId(id);
        if (chatService != null) chatService.removeUser(id);
        userRepository.delete(user);
        log.info("ADMIN action: disabled user permanently deleted id={} email={}", id, user.getEmail());
    }

    private User requireUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")); }
    private String requireText(String value, String field) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required"); return value.trim(); }
    private String requireLoginId(String value) {
        String loginId = requireText(value, "Username or email").toLowerCase(java.util.Locale.ROOT);
        if (loginId.length() > 254 || loginId.chars().anyMatch(Character::isWhitespace))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username or email must not contain spaces and must be 254 characters or fewer");
        return loginId;
    }
    private String requirePassword(String value) {
        if (value == null || value.length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        return value;
    }
    private void lockAndProtectLastAdmin(User user, Role nextRole, AccountStatus nextStatus) {
        if (user.getRole() == Role.ADMIN && user.getStatus() == AccountStatus.ACTIVE && (nextRole != Role.ADMIN || nextStatus != AccountStatus.ACTIVE)
                && userRepository.findByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE).size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active ADMIN cannot be disabled or demoted");
        }
    }

    private UserResponse toResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartment().getId(),
                user.getDepartment().getName(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

package com.iodsky.sweldox.batch.user;

import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserRole;
import com.iodsky.sweldox.security.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ItemProcessor for transforming UserImportRecord to User entity with validation.
 * Uses in-memory caching to optimize database lookups for reference data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserImportProcessor implements ItemProcessor<UserImportRecord, User> {

    private final EmployeeService employeeService;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    private Map<String, UserRole> userRoleCache;

    @Override
    public User process(UserImportRecord item) throws Exception {
        log.debug("Processing user: {} {}", item.getEmployeeId(), item.getEmail());

        // Initialize cache on first run (lazy loading)
        initializeCache();

        User user = User.builder()
                .email(item.getEmail())
                .build();

        // Validate and set employee
        long employeeId = Long.parseLong(item.getEmployeeId());
        Employee employee = employeeService.getEmployeeById(employeeId);
        user.setEmployee(employee);

        // Validate and set role using cache
        UserRole role = userRoleCache.get(item.getRole());
        if (role == null) {
            throw new IllegalArgumentException("Invalid user role: " + item.getRole());
        }
        user.setUserRole(role);

        // Encode password
        String password = passwordEncoder.encode(item.getPassword());
        user.setPassword(password);

        log.debug("Successfully processed user: {} {}", item.getEmployeeId(), item.getEmail());
        return user;
    }

    /**
     * Initialize cache with user roles from database.
     * This is called once before processing the first item.
     */
    private void initializeCache() {
        if (userRoleCache == null) {
            log.info("Initializing user role cache...");

            userRoleCache = new HashMap<>();
            List<UserRole> userRoles = userRoleRepository.findAll();
            for (UserRole userRole : userRoles) {
                userRoleCache.put(userRole.getRole(), userRole);
            }
            log.info("Loaded {} user roles into cache", userRoles.size());
        }
    }

}


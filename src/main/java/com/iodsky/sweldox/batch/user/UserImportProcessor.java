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

@Component
@RequiredArgsConstructor
@Slf4j
public class UserImportProcessor implements ItemProcessor<UserImportRecord, User> {

    private final EmployeeService employeeService;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User process(UserImportRecord item) throws Exception {
        log.debug("Processing user: {} {}", item.getEmployeeId(), item.getEmail());

        User user = User.builder()
                .email(item.getEmail())
                .build();

        // Validate and set employee
        long employeeId = Long.parseLong(item.getEmployeeId());
        Employee employee = employeeService.getEmployeeById(employeeId);
        user.setEmployee(employee);

        // Validate and set role
        UserRole role = userRoleRepository.findById(item.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Invalid user role " + item.getRole()));
        user.setUserRole(role);

        // Encode password
        String password = passwordEncoder.encode(item.getPassword());
        user.setPassword(password);

        log.debug("Successfully processed user: {} {}", item.getEmployeeId(), item.getEmail());
        return user;
    }


}

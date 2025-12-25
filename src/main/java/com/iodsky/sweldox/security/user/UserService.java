package com.iodsky.sweldox.security.user;

import com.iodsky.sweldox.common.exception.CsvImportException;
import com.iodsky.sweldox.csvimport.CsvResult;
import com.iodsky.sweldox.csvimport.CsvService;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;
    private final CsvService<User, UserCsvRecord> userCsvService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " not found"));
    }

    public User createUser(UserRequest userRequest) {

        User user = userMapper.toEntity(userRequest);

        Employee employee = employeeService.getEmployeeById(userRequest.getEmployeeId());
        user.setEmployee(employee);

        UserRole role = getUserRole(userRequest.getRole());
        user.setUserRole(role);

        user.setPassword(passwordEncoder.encode(userRequest.getPassword()));

        return userRepository.save(user);
    }

    public Integer importUsers(MultipartFile file) {
        try {
            LinkedHashSet<CsvResult<User, UserCsvRecord>> records =
                    userCsvService.parseCsv(file.getInputStream(), UserCsvRecord.class);

            LinkedHashSet<User> users = records.stream().map(r -> {
                        User user = r.entity();
                        UserCsvRecord csv = r.source();

                        Employee employee = employeeService.getEmployeeById(csv.getEmployeeId());
                        UserRole role = getUserRole(csv.getRole());

                        user.setEmployee(employee);
                        user.setUserRole(role);

                        user.setPassword(passwordEncoder.encode(user.getPassword()));

                        return user;
                    })
                    .filter(u -> !userRepository.existsByEmail(u.getEmail()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            userRepository.saveAll(users);
            return users.size();
        } catch (IOException e) {
            throw new CsvImportException(e.getMessage());
        }
    }

    public Page<User> getAllUsers(int size, int limit, String role) {
        Pageable pageable = PageRequest.of(size, limit);
        if (role == null) {
            return userRepository.findAll(pageable);
        }

        if (!userRoleRepository.existsByRole(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + role);
        }

        return userRepository.findUserByUserRole_Role(role, pageable);
    }

    public User getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {
            return user;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user found");
    }

    private UserRole getUserRole(String role) {
        return userRoleRepository.findById(role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role " + role));
    }

}

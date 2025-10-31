package com.iodsky.motorph.security.user;

import com.iodsky.motorph.common.exception.BadRequestException;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private EmployeeService employeeService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserService userService;

    private User user;
    private UserRequest userRequest;
    private Employee employee;
    private UserRole role;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);

        role = new UserRole("HR");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("john.doe@example.com");
        user.setPassword("encoded-pass");
        user.setEmployee(employee);
        user.setUserRole(role);

        userRequest = new UserRequest();
        userRequest.setEmployeeId(1L);
        userRequest.setPassword("password123");
        userRequest.setRole("HR");
    }

    @Nested
    class LoadUserByUsernameTests {
        @Test
        void shouldReturnUserDetailsWhenUserExists() {
            when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(user));

            UserDetails result = userService.loadUserByUsername("john.doe@example.com");

            assertNotNull(result);
            assertEquals("john.doe@example.com", result.getUsername());
            verify(userRepository).findByEmail("john.doe@example.com");
        }

        @Test
        void shouldThrowNotFoundExceptionWhenUserDoesNotExist() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> userService.loadUserByUsername("missing@example.com"));

            assertEquals("User missing@example.com not found", ex.getMessage());
        }

        @Test
        void shouldHandleNullUsernameGracefully() {
            when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> userService.loadUserByUsername(null));
        }
    }

    @Nested
    class GetAllUsersTests {

        @Test
        void shouldReturnAllUsersWhenRoleIsNull() {
            when(userRepository.findAll()).thenReturn(List.of(user));

            List<User> result = userService.getAllUsers(null);

            assertEquals(1, result.size());
            verify(userRepository).findAll();
            verifyNoInteractions(userRoleRepository);
        }

        @Test
        void shouldReturnUsersByRoleWhenValidRoleExists() {
            when(userRoleRepository.existsByRole("HR")).thenReturn(true);
            when(userRepository.findUserByUserRole_Role("HR")).thenReturn(List.of(user));

            List<User> result = userService.getAllUsers("HR");

            assertEquals(1, result.size());
            verify(userRepository).findUserByUserRole_Role("HR");
        }

        @Test
        void shouldThrowBadRequestWhenInvalidRoleProvided() {
            when(userRoleRepository.existsByRole("INVALID")).thenReturn(false);

            assertThrows(BadRequestException.class, () -> userService.getAllUsers("INVALID"));
        }
    }

    @Nested
    class CreateUserTests {

        @Test
        void shouldCreateUserSuccessfully() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("password123")).thenReturn("encoded-pass");
            when(userRepository.save(any(User.class))).thenReturn(user);

            User result = userService.createUser(userRequest);

            assertNotNull(result);
            assertEquals("encoded-pass", result.getPassword());
            assertEquals(employee, result.getEmployee());
            assertEquals(role, result.getUserRole());
            verify(userRepository).save(any(User.class));
        }

        @Test
        void shouldThrowNotFoundWhenEmployeeDoesNotExist() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenThrow(new NotFoundException("Employee not found"));

            assertThrows(NotFoundException.class, () -> userService.createUser(userRequest));
        }

        @Test
        void shouldThrowBadRequestWhenRoleNotFound() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.empty());

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> userService.createUser(userRequest));

            assertEquals("Invalid role HR", ex.getMessage());
        }

        @Test
        void shouldEncodePasswordBeforeSaving() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("password123")).thenReturn("ENCODED123");
            when(userRepository.save(any(User.class))).thenReturn(user);

            User result = userService.createUser(userRequest);

            verify(passwordEncoder).encode("password123");
            assertEquals("ENCODED123", result.getPassword()); // the user object was preset
        }

        @Test
        void shouldPropagateRepositoryErrors() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB failure"));

            assertThrows(RuntimeException.class, () -> userService.createUser(userRequest));
        }
    }
}

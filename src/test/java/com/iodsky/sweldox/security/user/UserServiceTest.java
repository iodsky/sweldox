package com.iodsky.sweldox.security.user;

import com.iodsky.sweldox.common.exception.CsvImportException;
import com.iodsky.sweldox.csvimport.CsvResult;
import com.iodsky.sweldox.csvimport.CsvService;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
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
    @Mock private CsvService<User, UserCsvRecord> csvService;

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

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.loadUserByUsername("missing@example.com"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            assertEquals("User missing@example.com not found", ex.getReason());
        }

        @Test
        void shouldHandleNullUsernameGracefully() {
            when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.loadUserByUsername(null));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Nested
    class GetAllUsersTests {

        @Test
        void shouldReturnAllUsersWhenRoleIsNull() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> userPage = new PageImpl<>(List.of(user), pageable, 1);
            when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);

            Page<User> result = userService.getAllUsers(0, 10, null);

            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
            verify(userRepository).findAll(any(Pageable.class));
            verifyNoInteractions(userRoleRepository);
        }

        @Test
        void shouldReturnUsersByRoleWhenValidRoleExists() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> userPage = new PageImpl<>(List.of(user), pageable, 1);
            when(userRoleRepository.existsByRole("HR")).thenReturn(true);
            when(userRepository.findUserByUserRole_Role(eq("HR"), any(Pageable.class))).thenReturn(userPage);

            Page<User> result = userService.getAllUsers(0, 10, "HR");

            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
            verify(userRepository).findUserByUserRole_Role(eq("HR"), any(Pageable.class));
        }

        @Test
        void shouldThrowBadRequestWhenInvalidRoleProvided() {
            when(userRoleRepository.existsByRole("INVALID")).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.getAllUsers(0, 10, "INVALID"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
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
            when(employeeService.getEmployeeById(1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.createUser(userRequest));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void shouldThrowBadRequestWhenRoleNotFound() {
            when(userMapper.toEntity(any(UserRequest.class))).thenReturn(user);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.createUser(userRequest));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals("Invalid role HR", ex.getReason());
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

    @Nested
    class GetUserRoleTests {

    }
    @Nested
    class ImportUsersTests {

        private UserCsvRecord csvRecord;
        private User csvUser;

        @BeforeEach
        void setUpImportTests() {
            csvRecord = new UserCsvRecord();
            csvRecord.setEmployeeId(1L);
            csvRecord.setRole("HR");

            csvUser = new User();
            csvUser.setEmail("csv.user@example.com");
            csvUser.setPassword("plainPassword");
        }

        @Test
        void shouldImportUsersSuccessfully() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            LinkedHashSet<CsvResult<User, UserCsvRecord>> records = new LinkedHashSet<>(Set.of(csvResult));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("plainPassword")).thenReturn("encodedPassword");
            when(userRepository.existsByEmail("csv.user@example.com")).thenReturn(false);
            when(userRepository.saveAll(anySet())).thenReturn(List.of(csvUser));

            Integer result = userService.importUsers(file);

            assertEquals(1, result);
            verify(csvService).parseCsv(any(InputStream.class), eq(UserCsvRecord.class));
            verify(userRepository).saveAll(anySet());
        }

        @Test
        void shouldFilterOutExistingUsers() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            LinkedHashSet<CsvResult<User, UserCsvRecord>> records = new LinkedHashSet<>(Set.of(csvResult));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.existsByEmail("csv.user@example.com")).thenReturn(true);
            when(userRepository.saveAll(anySet())).thenReturn(List.of());

            Integer result = userService.importUsers(file);

            assertEquals(0, result);
            verify(userRepository).saveAll(argThat(set -> ((Set<?>) set).isEmpty()));
        }

        @Test
        void shouldEncodePasswordsForImportedUsers() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            LinkedHashSet<CsvResult<User, UserCsvRecord>> records = new LinkedHashSet<>(Set.of(csvResult));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("plainPassword")).thenReturn("ENCODED_PASSWORD");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.saveAll(anySet())).thenReturn(List.of(csvUser));

            userService.importUsers(file);

            verify(passwordEncoder).encode("plainPassword");
        }

        @Test
        void shouldWrapIOExceptionInCsvImportException() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getInputStream()).thenThrow(new IOException("File read error"));

            CsvImportException ex = assertThrows(CsvImportException.class,
                    () -> userService.importUsers(file));

            assertEquals("File read error", ex.getMessage());
            verifyNoInteractions(csvService, employeeService, userRoleRepository, passwordEncoder, userRepository);
        }

        @Test
        void shouldSetEmployeeAndRoleForEachImportedUser() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            LinkedHashSet<CsvResult<User, UserCsvRecord>> records = new LinkedHashSet<>(Set.of(csvResult));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.saveAll(anySet())).thenReturn(List.of(csvUser));

            userService.importUsers(file);

            verify(employeeService).getEmployeeById(1L);
            verify(userRoleRepository).findById("HR");
        }
    }
}

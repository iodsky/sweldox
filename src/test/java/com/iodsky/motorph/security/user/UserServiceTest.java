package com.iodsky.motorph.security.user;

import com.iodsky.motorph.common.exception.BadRequestException;
import com.iodsky.motorph.common.exception.CsvImportException;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.csvimport.CsvResult;
import com.iodsky.motorph.csvimport.CsvService;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

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

            assertThrows(BadRequestException.class, () -> userService.getAllUsers(0, 10, "INVALID"));
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

    @Nested
    class GetUserRoleTests {

        @Test
        void shouldReturnRoleWhenRoleExists() {
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));

            UserRole result = userService.getUserRole("HR");

            assertNotNull(result);
            assertEquals("HR", result.getRole());
            verify(userRoleRepository).findById("HR");
        }

        @Test
        void shouldThrowBadRequestWhenRoleDoesNotExist() {
            when(userRoleRepository.findById("INVALID")).thenReturn(Optional.empty());

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> userService.getUserRole("INVALID"));

            assertEquals("Invalid role INVALID", ex.getMessage());
        }
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
            List<CsvResult<User, UserCsvRecord>> records = List.of(csvResult);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("plainPassword")).thenReturn("encodedPassword");
            when(userRepository.existsByEmail("csv.user@example.com")).thenReturn(false);
            when(userRepository.saveAll(anyList())).thenReturn(List.of(csvUser));

            Integer result = userService.importUsers(file);

            assertEquals(1, result);
            verify(csvService).parseCsv(any(InputStream.class), eq(UserCsvRecord.class));
            verify(userRepository).saveAll(anyList());
        }

        @Test
        void shouldFilterOutExistingUsers() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            List<CsvResult<User, UserCsvRecord>> records = List.of(csvResult);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.existsByEmail("csv.user@example.com")).thenReturn(true);
            when(userRepository.saveAll(anyList())).thenReturn(List.of());

            Integer result = userService.importUsers(file);

            assertEquals(0, result);
            verify(userRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
        }

        @Test
        void shouldEncodePasswordsForImportedUsers() throws IOException {
            CsvResult<User, UserCsvRecord> csvResult = new CsvResult<>(csvUser, csvRecord);
            List<CsvResult<User, UserCsvRecord>> records = List.of(csvResult);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("plainPassword")).thenReturn("ENCODED_PASSWORD");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.saveAll(anyList())).thenReturn(List.of(csvUser));

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
            List<CsvResult<User, UserCsvRecord>> records = List.of(csvResult);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "users.csv", "text/csv", "csv content".getBytes());

            when(csvService.parseCsv(any(InputStream.class), eq(UserCsvRecord.class))).thenReturn(records);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(userRoleRepository.findById("HR")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.saveAll(anyList())).thenReturn(List.of(csvUser));

            userService.importUsers(file);

            verify(employeeService).getEmployeeById(1L);
            verify(userRoleRepository).findById("HR");
        }
    }
}

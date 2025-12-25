package com.iodsky.sweldox.leave;

import com.iodsky.sweldox.common.exception.CsvImportException;
import com.iodsky.sweldox.csvimport.CsvResult;
import com.iodsky.sweldox.csvimport.CsvService;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveCreditServiceTest {

    @Mock private LeaveCreditRepository leaveCreditRepository;
    @Mock private EmployeeService employeeService;
    @Mock private CsvService<LeaveCredit, LeaveCreditCsvRecord> leaveCreditCsvService;
    @InjectMocks private LeaveCreditService leaveCreditService;

    private User normalUser;
    private Employee employee;
    private LeaveCredit vacationCredit;
    private LeaveCredit sickCredit;

    @BeforeEach
    void setUp() {
        // Setup employee
        employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");

        // Setup user
        UserRole normalRole = new UserRole();
        normalRole.setRole("EMPLOYEE");

        normalUser = new User();
        normalUser.setId(UUID.randomUUID());
        normalUser.setEmployee(employee);
        normalUser.setUserRole(normalRole);

        // Setup leave credits
        vacationCredit = LeaveCredit.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .type(LeaveType.VACATION)
                .credits(10.0)
                .build();

        sickCredit = LeaveCredit.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .type(LeaveType.SICK)
                .credits(5.0)
                .build();
    }

    @Nested
    class InitializeEmployeeLeaveCreditsTests {

        @Test
        void shouldInitializeLeaveCreditsSuccessfullyWithProvidedFiscalYear() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(1L, "2025-2026")).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            assertNotNull(result);
            assertEquals(3, result.size());

            // Verify VACATION leave credit
            LeaveCredit vacation = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.VACATION)
                    .findFirst()
                    .orElse(null);
            assertNotNull(vacation);
            assertEquals(14.0, vacation.getCredits());
            assertEquals("2025-2026", vacation.getFiscalYear());
            assertEquals(employee, vacation.getEmployee());

            // Verify SICK leave credit
            LeaveCredit sick = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.SICK)
                    .findFirst()
                    .orElse(null);
            assertNotNull(sick);
            assertEquals(7.0, sick.getCredits());
            assertEquals("2025-2026", sick.getFiscalYear());
            assertEquals(employee, sick.getEmployee());

            // Verify BEREAVEMENT leave credit
            LeaveCredit bereavement = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.BEREAVEMENT)
                    .findFirst()
                    .orElse(null);
            assertNotNull(bereavement);
            assertEquals(5.0, bereavement.getCredits());
            assertEquals("2025-2026", bereavement.getFiscalYear());
            assertEquals(employee, bereavement.getEmployee());

            verify(employeeService).getEmployeeById(1L);
            verify(leaveCreditRepository).existsByEmployee_IdAndFiscalYear(1L, "2025-2026");
            verify(leaveCreditRepository).saveAll(any());
        }

        @Test
        void shouldInitializeLeaveCreditsWithDefaultFiscalYearWhenNotProvided() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear(null);

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(eq(1L), any())).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            assertNotNull(result);
            assertEquals(3, result.size());

            // Verify that fiscal year is generated (current year format)
            String fiscalYear = result.getFirst().getFiscalYear();
            assertNotNull(fiscalYear);
            assertTrue(fiscalYear.matches("\\d{4}-\\d{4}"));

            // All credits should have the same fiscal year
            assertTrue(result.stream().allMatch(lc -> lc.getFiscalYear().equals(fiscalYear)));

            verify(leaveCreditRepository).saveAll(any());
        }

        @Test
        void shouldInitializeLeaveCreditsWithDefaultFiscalYearWhenBlankProvided() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("   ");

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(eq(1L), any())).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            assertNotNull(result);
            assertEquals(3, result.size());

            // Verify that fiscal year is generated despite blank string
            String fiscalYear = result.get(0).getFiscalYear();
            assertNotNull(fiscalYear);
            assertTrue(fiscalYear.matches("\\d{4}-\\d{4}"));

            verify(leaveCreditRepository).saveAll(any());
        }

        @Test
        void shouldThrowConflictExceptionWhenLeaveCreditsAlreadyExist() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(1L))
                    .thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(1L, "2025-2026"))
                    .thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.initializeEmployeeLeaveCredits(dto));

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("Leave credits already exists"));
            assertTrue(ex.getMessage().contains("employee 1"));

            verify(employeeService).getEmployeeById(1L);
            verify(leaveCreditRepository).existsByEmployee_IdAndFiscalYear(1L, "2025-2026");
            verify(leaveCreditRepository, never()).saveAll(any());
        }

        @Test
        void shouldThrowNotFoundExceptionWhenEmployeeDoesNotExist() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(999L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(999L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found: 999"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.initializeEmployeeLeaveCredits(dto));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            verify(employeeService).getEmployeeById(999L);
            verify(leaveCreditRepository, never()).existsByEmployee_IdAndFiscalYear(any(), any());
            verify(leaveCreditRepository, never()).saveAll(any());
        }

        @Test
        void shouldCreateAllThreeLeaveTypes() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(1L, "2025-2026")).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            // Verify all three types exist
            long vacationCount = result.stream().filter(lc -> lc.getType() == LeaveType.VACATION).count();
            long sickCount = result.stream().filter(lc -> lc.getType() == LeaveType.SICK).count();
            long bereavementCount = result.stream().filter(lc -> lc.getType() == LeaveType.BEREAVEMENT).count();

            assertEquals(1, vacationCount);
            assertEquals(1, sickCount);
            assertEquals(1, bereavementCount);
        }

        @Test
        void shouldUseCorrectDefaultCreditsForEachLeaveType() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(1L, "2025-2026")).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            // Verify default credits match constants
            LeaveCredit vacation = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.VACATION)
                    .findFirst()
                    .orElseThrow();
            assertEquals(14.0, vacation.getCredits());

            LeaveCredit sick = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.SICK)
                    .findFirst()
                    .orElseThrow();
            assertEquals(7.0, sick.getCredits());

            LeaveCredit bereavement = result.stream()
                    .filter(lc -> lc.getType() == LeaveType.BEREAVEMENT)
                    .findFirst()
                    .orElseThrow();
            assertEquals(5.0, bereavement.getCredits());
        }

        @Test
        void shouldAssociateAllCreditsWithCorrectEmployee() {
            InitializeEmployeeLeaveCreditsDto dto = new InitializeEmployeeLeaveCreditsDto();
            dto.setEmployeeId(1L);
            dto.setFiscalYear("2025-2026");

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.existsByEmployee_IdAndFiscalYear(1L, "2025-2026")).thenReturn(false);
            when(leaveCreditRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<LeaveCredit> result = leaveCreditService.initializeEmployeeLeaveCredits(dto);

            // Verify all credits are associated with the correct employee
            assertTrue(result.stream().allMatch(lc -> lc.getEmployee().equals(employee)));
        }
    }

    @Nested
    class GetLeaveCreditByEmployeeIdAndTypeTests {

        @Test
        void shouldReturnLeaveCreditSuccessfully() {
            when(leaveCreditRepository.findByEmployee_IdAndType(eq(1L), eq(LeaveType.VACATION)))
                    .thenReturn(Optional.of(vacationCredit));

            LeaveCredit result = leaveCreditService.getLeaveCreditByEmployeeIdAndType(1L, LeaveType.VACATION);

            assertNotNull(result);
            assertEquals(LeaveType.VACATION, result.getType());
            assertEquals(10.0, result.getCredits());
        }

        @Test
        void shouldThrowNotFoundWhenLeaveCreditDoesNotExist() {
            when(leaveCreditRepository.findByEmployee_IdAndType(eq(1L), eq(LeaveType.VACATION)))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.getLeaveCreditByEmployeeIdAndType(1L, LeaveType.VACATION));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("VACATION"));
            assertTrue(ex.getMessage().contains("employeeId: 1"));
        }
    }

    @Nested
    class GetLeaveCreditsByEmployeeIdTests {

        @Test
        void shouldReturnAllLeaveCreditsForAuthenticatedEmployee() {
            Authentication authentication = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(authentication.getPrincipal()).thenReturn(normalUser);
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);

            when(leaveCreditRepository.findAllByEmployee_Id(eq(1L)))
                    .thenReturn(List.of(vacationCredit, sickCredit));

            List<LeaveCredit> result = leaveCreditService.getLeaveCreditsByEmployeeId();

            assertEquals(2, result.size());
            verify(leaveCreditRepository).findAllByEmployee_Id(eq(1L));
        }

        @Test
        void shouldThrowUnauthorizedWhenPrincipalIsNotUser() {
            Authentication authentication = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.getLeaveCreditsByEmployeeId());

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }
    }

    @Nested
    class UpdateLeaveCreditTests {

        @Test
        void shouldUpdateLeaveCreditSuccessfully() {
            UUID creditId = vacationCredit.getId();
            LeaveCredit updatedCredit = LeaveCredit.builder()
                    .id(creditId)
                    .employee(employee)
                    .type(LeaveType.VACATION)
                    .credits(8.0)
                    .build();

            when(leaveCreditRepository.findById(creditId))
                    .thenReturn(Optional.of(vacationCredit));
            when(leaveCreditRepository.save(any(LeaveCredit.class)))
                    .thenReturn(updatedCredit);

            LeaveCredit result = leaveCreditService.updateLeaveCredit(creditId, updatedCredit);

            assertNotNull(result);
            assertEquals(8.0, result.getCredits());
            verify(leaveCreditRepository).save(vacationCredit);
        }

        @Test
        void shouldThrowNotFoundWhenLeaveCreditDoesNotExist() {
            UUID creditId = UUID.randomUUID();
            when(leaveCreditRepository.findById(creditId))
                    .thenReturn(Optional.empty());

            LeaveCredit updatedCredit = LeaveCredit.builder()
                    .credits(8.0)
                    .build();

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.updateLeaveCredit(creditId, updatedCredit));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Nested
    class ImportLeaveCreditsTests {

        @Test
        void shouldImportLeaveCreditsSuccessfully() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            InputStream inputStream = new ByteArrayInputStream("test data".getBytes());
            when(file.getInputStream()).thenReturn(inputStream);

            LeaveCreditCsvRecord csvRecord1 = new LeaveCreditCsvRecord();
            csvRecord1.setEmployeeId(1L);
            csvRecord1.setType("VACATION");
            csvRecord1.setCredits(10.0);

            LeaveCreditCsvRecord csvRecord2 = new LeaveCreditCsvRecord();
            csvRecord2.setEmployeeId(1L);
            csvRecord2.setType("SICK");
            csvRecord2.setCredits(5.0);

            LeaveCredit entity1 = LeaveCredit.builder()
                    .credits(10.0)
                    .build();

            LeaveCredit entity2 = LeaveCredit.builder()
                    .credits(5.0)
                    .build();

            CsvResult<LeaveCredit, LeaveCreditCsvRecord> result1 = new CsvResult<>(entity1, csvRecord1);
            CsvResult<LeaveCredit, LeaveCreditCsvRecord> result2 = new CsvResult<>(entity2, csvRecord2);

            LinkedHashSet<CsvResult<LeaveCredit, LeaveCreditCsvRecord>> csvResults = new LinkedHashSet<>();
            csvResults.add(result1);
            csvResults.add(result2);

            when(leaveCreditCsvService.parseCsv(any(InputStream.class), eq(LeaveCreditCsvRecord.class)))
                    .thenReturn(csvResults);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.saveAll(any())).thenReturn(List.of(entity1, entity2));

            Integer count = leaveCreditService.importLeaveCredits(file);

            assertEquals(2, count);
            verify(leaveCreditRepository).saveAll(any());
        }

        @Test
        void shouldThrowBadRequestWhenInvalidLeaveType() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            InputStream inputStream = new ByteArrayInputStream("test data".getBytes());
            when(file.getInputStream()).thenReturn(inputStream);

            LeaveCreditCsvRecord csvRecord = new LeaveCreditCsvRecord();
            csvRecord.setEmployeeId(1L);
            csvRecord.setType("INVALID_TYPE");
            csvRecord.setCredits(10.0);

            LeaveCredit entity = LeaveCredit.builder()
                    .credits(10.0)
                    .build();

            CsvResult<LeaveCredit, LeaveCreditCsvRecord> result = new CsvResult<>(entity, csvRecord);

            LinkedHashSet<CsvResult<LeaveCredit, LeaveCreditCsvRecord>> csvResults = new LinkedHashSet<>();
            csvResults.add(result);

            when(leaveCreditCsvService.parseCsv(any(InputStream.class), eq(LeaveCreditCsvRecord.class)))
                    .thenReturn(csvResults);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    leaveCreditService.importLeaveCredits(file));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("Invalid leave type"));
        }

        @Test
        void shouldThrowCsvImportExceptionWhenIOExceptionOccurs() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getInputStream()).thenThrow(new IOException("File read error"));

            assertThrows(CsvImportException.class, () ->
                    leaveCreditService.importLeaveCredits(file));
        }

        @Test
        void shouldResolveEmployeeCorrectly() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            InputStream inputStream = new ByteArrayInputStream("test data".getBytes());
            when(file.getInputStream()).thenReturn(inputStream);

            LeaveCreditCsvRecord csvRecord = new LeaveCreditCsvRecord();
            csvRecord.setEmployeeId(1L);
            csvRecord.setType("VACATION");
            csvRecord.setCredits(10.0);

            LeaveCredit entity = LeaveCredit.builder()
                    .credits(10.0)
                    .build();

            CsvResult<LeaveCredit, LeaveCreditCsvRecord> result = new CsvResult<>(entity, csvRecord);

            LinkedHashSet<CsvResult<LeaveCredit, LeaveCreditCsvRecord>> csvResults = new LinkedHashSet<>();
            csvResults.add(result);

            when(leaveCreditCsvService.parseCsv(any(InputStream.class), eq(LeaveCreditCsvRecord.class)))
                    .thenReturn(csvResults);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(leaveCreditRepository.saveAll(any())).thenReturn(List.of(entity));

            leaveCreditService.importLeaveCredits(file);

            verify(employeeService).getEmployeeById(1L);
        }
    }
}


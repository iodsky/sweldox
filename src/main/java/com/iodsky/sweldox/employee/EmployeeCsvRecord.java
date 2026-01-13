package com.iodsky.sweldox.employee;

import com.iodsky.sweldox.csvimport.LocalDateCsvConverter;
import com.iodsky.sweldox.csvimport.LocalTimeCsvConverter;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeCsvRecord {

    @CsvBindByName(column = "Last Name")
    private String lastName;

    @CsvBindByName(column = "First Name")
    private String firstName;

    @CsvCustomBindByName(column = "Birthday", converter = LocalDateCsvConverter.class)
    private LocalDate birthday;

    @CsvBindByName(column = "Address")
    private String address;

    @CsvBindByName(column = "Phone Number")
    private String phoneNumber;

    @CsvBindByName(column = "SSS #")
    private String sssId;

    @CsvBindByName(column = "Philhealth #")
    private String philhealthId;

    @CsvBindByName(column = "Tin #")
    private String tinId;

    @CsvBindByName(column = "Pag-ibig #")
    private String pagibigId;

    @CsvBindByName(column = "Status")
    private String status;

    @CsvBindByName(column = "Position")
    private String position;

    @CsvBindByName(column = "Supervisor")
    private Long supervisorId;

    @CsvBindByName(column = "Basic Salary")
    private BigDecimal basicSalary;

    @CsvBindByName(column = "Meal Allowance")
    private BigDecimal mealAllowance;

    @CsvBindByName(column = "Phone Allowance")
    private BigDecimal phoneAllowance;

    @CsvBindByName(column = "Clothing Allowance")
    private BigDecimal clothingAllowance;

    @CsvBindByName(column = "Semi-monthly Rate")
    private BigDecimal semiMonthlyRate;

    @CsvBindByName(column = "Hourly Rate")
    private BigDecimal hourlyRate;

    @CsvCustomBindByName(column = "Start Shift", converter = LocalTimeCsvConverter.class)
    private LocalTime startShift;

    @CsvCustomBindByName(column = "End Shift", converter = LocalTimeCsvConverter.class)
    private LocalTime endShift;

}

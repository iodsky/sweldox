package com.iodsky.sweldox.batch.payroll;

import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.payroll.Payroll;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class GeneratePayrollJobConfig {

    private final EmployeeService employeeService;

    @Bean
    public ItemReader<Long> employeeIdReader() {
        return new ListItemReader<>(employeeService.getAllActiveEmployeeIds());
    }


    @Bean
    public JpaItemWriter<Payroll> payrollWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<Payroll>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step generatePayrollStep(ItemReader<Long> employeeIdReader,
                                    PayrollitemProcessor payrollItemProcessor,
                                    JpaItemWriter<Payroll> payrollWriter,
                                    JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager) {
        return new StepBuilder("generatePayrollStep", jobRepository)
                .<Long, Payroll>chunk(10, transactionManager)
                .reader(employeeIdReader)
                .processor(payrollItemProcessor)
                .writer(payrollWriter)
                .faultTolerant()
                .skip(RuntimeException.class)
                .skipLimit(100)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Job generatePayrollJob(Step generatePayrollStep, JobRepository jobRepository) {
        return new JobBuilder("generatePayrollJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(generatePayrollStep)
                .build();
    }

}

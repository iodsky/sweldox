package com.iodsky.sweldox.batch.employee;

import com.iodsky.sweldox.batch.ImportJobExecutionListener;
import com.iodsky.sweldox.employee.Employee;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class EmployeeImportJobConfig {

    private final EmployeeImportProcessor employeeCsvRowProcessor;
    private final EmployeeImportSkipListener skipListener;
    private final ImportJobExecutionListener jobCompletionListener;

    @Value("${batch.upload.directory}")
    private String uploadDirectory;

    @Bean
    @StepScope
    public FlatFileItemReader<EmployeeImportRecord> employeeCsvReader(
            @Value("#{jobParameters['fileName']}") String fileName) {
        return new FlatFileItemReaderBuilder<EmployeeImportRecord>()
                .linesToSkip(1)
                .name("employeeCsvItemReader")
                .resource(new FileSystemResource(Paths.get(uploadDirectory, fileName).toFile()))
                .delimited()
                .delimiter(",")
                .names(EmployeeImportRecord.CSV_COLUMN_NAMES)
                .targetType(EmployeeImportRecord.class)
                .build();
    }

    @Bean
    public ItemProcessor<EmployeeImportRecord, Employee> employeeProcessor() {
        return employeeCsvRowProcessor;
    }

    @Bean
    public JpaItemWriter<Employee> employeeWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<Employee>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step employeeImportStep(ItemReader<EmployeeImportRecord> employeeCsvReader,
                                   ItemProcessor<EmployeeImportRecord, Employee> employeeProcessor,
                                   JpaItemWriter<Employee> employeeWriter,
                                   JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("importEmployeesStep", jobRepository)
                .<EmployeeImportRecord, Employee>chunk(10, transactionManager)
                .reader(employeeCsvReader)
                .processor(employeeProcessor)
                .writer(employeeWriter)
                .faultTolerant()
                .skip(DataIntegrityViolationException.class)
                .skipLimit(100)
                .listener(skipListener)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Job employeeImportJob(Step employeeImportStep, JobRepository jobRepository) {
        return new JobBuilder("importEmployeesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobCompletionListener)
                .start(employeeImportStep)
                .build();
    }

}

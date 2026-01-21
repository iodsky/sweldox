package com.iodsky.sweldox.batch.user;

import com.iodsky.sweldox.batch.ImportJobExecutionListener;
import com.iodsky.sweldox.security.user.User;
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
public class UserImportConfig {

    private final UserImportProcessor userImportProcessor;
    private final UserImportSkipListener skipListener;
    private final ImportJobExecutionListener jobCompletetionListener;

    @Value("${batch.upload.directory}")
    private String uploadDirectory;

    @Bean
    @StepScope
    public FlatFileItemReader<UserImportRecord> userCsvReader(
            @Value("#{jobParameters['fileName']}") String fileName) {
        return new FlatFileItemReaderBuilder<UserImportRecord>()
                .linesToSkip(1)
                .name("userCsvReader")
                .resource(new FileSystemResource(Paths.get(uploadDirectory, fileName).toFile()))
                .delimited()
                .delimiter(",")
                .names(UserImportRecord.CSV_COLUMN_NAMES)
                .targetType(UserImportRecord.class)
                .build();
    }

    @Bean
    public ItemProcessor<UserImportRecord, User> userProcessor() {
        return userImportProcessor;
    }

    @Bean
    public JpaItemWriter<User> userWritier(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<User>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step userImportStep(ItemReader<UserImportRecord> userCsvReader,
                                   ItemProcessor<UserImportRecord, User> userProcessor,
                                   JpaItemWriter<User> userWriter,
                                   JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("importUsersStep", jobRepository)
                .<UserImportRecord, User>chunk(10, transactionManager)
                .reader(userCsvReader)
                .processor(userProcessor)
                .writer(userWriter)
                .faultTolerant()
                .skip(DataIntegrityViolationException.class)
                .skipLimit(100)
                .listener(skipListener)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Job userImportJob(Step userImportStep, JobRepository jobRepository) {
        return new JobBuilder("importUsersJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(userImportStep)
                .listener(jobCompletetionListener)
                .build();
    }

}

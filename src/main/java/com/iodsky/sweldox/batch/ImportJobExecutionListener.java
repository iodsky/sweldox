package com.iodsky.sweldox.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Job completion listener that cleans up uploaded CSV files after processing.
 */
@Component
@Slf4j
public class ImportJobExecutionListener implements JobExecutionListener {

    @Value("${batch.upload.directory}")
    private String uploadDirectory;

    @Override
    public void afterJob(JobExecution jobExecution) {
        String fileName = jobExecution.getJobParameters().getString("fileName");

        if (!fileName.isEmpty()) {
            try {
                Path filePath = Paths.get(uploadDirectory, fileName);
                File file = filePath.toFile();

                if (file.exists()) {
                    if (Files.deleteIfExists(filePath)) {
                        log.info("Successfully deleted uploaded file: {} after job completion", fileName);
                    }
                } else {
                    log.warn("File {} not found for deletion", fileName);
                }
            } catch (Exception e) {
                log.error("Failed to delete uploaded file: {}. Error: {}", fileName, e.getMessage(), e);
            }
        }

        log.info("Job {} completed with status: {}",
                jobExecution.getJobId(),
                jobExecution.getStatus());
    }
}

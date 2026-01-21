package com.iodsky.sweldox.batch;

import com.iodsky.sweldox.batch.response.JobDetailsResponse;
import com.iodsky.sweldox.batch.response.JobLaunchResponse;
import com.iodsky.sweldox.common.response.ApiResponse;
import com.iodsky.sweldox.common.response.ResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@RestController
@RequestMapping("/job")
@Slf4j
@Tag(name = "Batch Jobs", description = "Batch job management endpoints")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job importEmployeesJob;
    private final Job userImportJob;

    @Value("${batch.upload.directory}")
    private String uploadDirectory;

    public BatchController(JobLauncher jobLauncher,
                          JobExplorer jobExplorer,
                          @Qualifier("employeeImportJob") Job importEmployeesJob,
                          @Qualifier("userImportJob") Job userImportJob) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.importEmployeesJob = importEmployeesJob;
        this.userImportJob = userImportJob;
    }

    @PreAuthorize("hasAnyRole('HR', 'IT')")
    @PostMapping(value = "/import-employees", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import employees from CSV file",
            description = "Upload a CSV file to import employees via batch job. Returns job execution ID for tracking."
    )
    public ResponseEntity<ApiResponse<JobLaunchResponse>> importEmployees(
            @RequestPart("file") MultipartFile file) {

        try {
            String fileName = uploadCsvFile(file);
            JobExecution jobExecution = launchJob(importEmployeesJob, fileName);

            JobLaunchResponse response = JobLaunchResponse.builder()
                    .jobExecutionId(jobExecution.getId())
                    .fileName(fileName)
                    .message("Employee import job launched successfully")
                    .build();

            return ResponseFactory.ok("Job launched successfully", response);

        } catch (Exception e) {
            log.error("Failed to launch employee import job", e);
            throw new RuntimeException("Failed to launch employee import job: " + e.getMessage(), e);
        }
    }

    @PreAuthorize("hasRole('IT')")
    @PostMapping(value = "/import-users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import users from CSV file",
            description = "Upload a CSV file to import users via batch job. Returns job execution ID for tracking. Restricted to IT role only."
    )
    public ResponseEntity<ApiResponse<JobLaunchResponse>> importUsers(
            @RequestPart("file") MultipartFile file) {

        try {
            String fileName = uploadCsvFile(file);
            JobExecution jobExecution = launchJob(userImportJob, fileName);

            JobLaunchResponse response = JobLaunchResponse.builder()
                    .jobExecutionId(jobExecution.getId())
                    .fileName(fileName)
                    .message("User import job launched successfully")
                    .build();

            return ResponseFactory.ok("Job launched successfully", response);

        } catch (Exception e) {
            log.error("Failed to launch user import job", e);
            throw new RuntimeException("Failed to launch user import job: " + e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAnyRole('HR', 'IT')")
    @GetMapping("/{jobExecutionId}")
    @Operation(
            summary = "Get job execution details",
            description = "Retrieve detailed information about a batch job execution including status and metrics."
    )
    public ResponseEntity<ApiResponse<JobDetailsResponse>> getJobExecutionDetails(
            @PathVariable Long jobExecutionId) {

        JobExecution jobExecution = jobExplorer.getJobExecution(jobExecutionId);

        // Get step execution metrics
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        long readCount = 0;
        long writeCount = 0;
        long skipCount = 0;

        for (StepExecution stepExecution : stepExecutions) {
            readCount += stepExecution.getReadCount();
            writeCount += stepExecution.getWriteCount();
            skipCount += stepExecution.getSkipCount();
        }

        String fileName = jobExecution.getJobParameters().getString("fileName");

        JobDetailsResponse details = JobDetailsResponse.builder()
                .jobExecutionId(jobExecution.getId())
                .status(jobExecution.getStatus())
                .fileName(fileName)
                .startTime(jobExecution.getStartTime())
                .endTime(jobExecution.getEndTime())
                .readCount(readCount)
                .writeCount(writeCount)
                .skipCount(skipCount)
                .exitDescription(jobExecution.getExitStatus().getExitDescription())
                .build();

        return ResponseFactory.ok("Job execution details retrieved successfully", details);
    }

    /**
     * Upload a CSV file to the upload directory with a timestamped filename.
     *
     * @param file the multipart file to upload
     * @return the generated filename
     * @throws Exception if file upload fails
     */
    private String uploadCsvFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (!file.getOriginalFilename().endsWith(".csv")) {
            throw new IllegalArgumentException("Only CSV files are supported");
        }

        // Create upload directory if it doesn't exist
        File uploadDir = new File(uploadDirectory);
        if (!uploadDir.exists()) {
            if (!uploadDir.mkdirs()) {
                throw new RuntimeException("Failed to create upload directory: " + uploadDirectory);
            }
        }

        // Generate unique filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalFilename = file.getOriginalFilename();
        String fileName = timestamp + "_" + originalFilename;

        // Save file to upload directory
        Path filePath = Paths.get(uploadDirectory, fileName);
        Files.copy(file.getInputStream(), filePath);

        log.info("File uploaded successfully: {}", fileName);

        return fileName;
    }

    /**
     * Launch a batch job with the given filename parameter.
     *
     * @param job the batch job to launch
     * @param fileName the filename parameter for the job
     * @return the job execution
     * @throws Exception if job launch fails
     */
    private JobExecution launchJob(Job job, String fileName) throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("fileName", fileName)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        return jobLauncher.run(job, jobParameters);
    }
}

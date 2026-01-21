package com.iodsky.sweldox.batch.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.batch.core.BatchStatus;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobDetailsResponse {
    private Long jobExecutionId;
    private BatchStatus status;
    private String fileName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long readCount;
    private Long writeCount;
    private Long skipCount;
    private String exitDescription;
}

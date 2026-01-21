package com.iodsky.sweldox.batch.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobLaunchResponse {
    private Long jobExecutionId;
    private String fileName;
    private String message;
}

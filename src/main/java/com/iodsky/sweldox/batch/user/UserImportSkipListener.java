package com.iodsky.sweldox.batch.user;

import com.iodsky.sweldox.security.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Skip listener for user import job to track and log skipped records
 * due to duplicate email violations, missing employee references, or other errors.
 */
@Component
@Slf4j
public class UserImportSkipListener implements SkipListener<UserImportRecord, User> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped record during read phase due to: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(UserImportRecord item, Throwable t) {
        log.warn("Skipped user with email {} during processing due to: {}",
                item.getEmail(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(@NonNull User item, @NonNull Throwable t) {
        String reason = "Unknown error";

        if (t instanceof DataIntegrityViolationException) {
            String message = t.getMessage();
            if (message != null) {
                if (message.contains("email")) {
                    reason = "Duplicate email: " + item.getEmail();
                } else if (message.contains("employee_id") || message.contains("employee")) {
                    reason = "Employee not found or invalid employee reference: " +
                            (item.getEmployee() != null ? item.getEmployee().getId() : "N/A");
                } else {
                    reason = "Duplicate constraint violation";
                }
            }
        } else {
            reason = t.getMessage();
        }

        log.warn("Skipped user with email {} during write phase. Reason: {}",
                item.getEmail(), reason);
    }
}

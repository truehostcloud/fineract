package org.apache.fineract.portfolio.self.security.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

public interface SelfServicePasswordResetWritePlatformService {

    CommandProcessingResult requestPasswordReset(JsonCommand command);

    CommandProcessingResult verifyAndUpdatePassword(JsonCommand command);
}

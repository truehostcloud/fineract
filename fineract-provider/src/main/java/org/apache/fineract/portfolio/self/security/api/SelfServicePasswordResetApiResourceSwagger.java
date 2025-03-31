package org.apache.fineract.portfolio.self.security.api;

import io.swagger.v3.oas.annotations.media.Schema;

public class SelfServicePasswordResetApiResourceSwagger {

    public static class PostSelfServicePasswordResetResponse {
        @Schema(example = "1")
        public Long resourceId;
    }

    public static class PostSelfServicePasswordResetVerifyResponse {
        @Schema(example = "1")
        public Long resourceId;
    }
} 
package org.apache.fineract.infrastructure.event.business.domain.document;

import org.apache.fineract.infrastructure.documentmanagement.domain.Document;

public class DocumentDeleteBusinessEvent extends DocumentBusinessEvent {
    private static final String TYPE = "DocumentDeleteBusinessEvent";

    public DocumentDeleteBusinessEvent(Document value) {
        super(value);
    }

    @Override
    public String getType() {
        return TYPE;
    }
} 
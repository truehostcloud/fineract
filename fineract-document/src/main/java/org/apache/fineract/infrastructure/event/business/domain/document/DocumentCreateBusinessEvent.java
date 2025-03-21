package org.apache.fineract.infrastructure.event.business.domain.document;

import org.apache.fineract.infrastructure.documentmanagement.domain.Document;

public class DocumentCreateBusinessEvent extends DocumentBusinessEvent {
    private static final String TYPE = "DocumentCreateBusinessEvent";

    public DocumentCreateBusinessEvent(Document value) {
        super(value);
    }

    @Override
    public String getType() {
        return TYPE;
    }
} 

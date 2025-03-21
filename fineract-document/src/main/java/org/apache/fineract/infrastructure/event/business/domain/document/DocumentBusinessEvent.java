package org.apache.fineract.infrastructure.event.business.domain.document;

import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.event.business.domain.AbstractBusinessEvent;

public abstract class DocumentBusinessEvent extends AbstractBusinessEvent<Document> {
    private static final String CATEGORY = "Document";

    public DocumentBusinessEvent(Document value) {
        super(value);
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Long getAggregateRootId() {
        return get().getId();
    }
} 
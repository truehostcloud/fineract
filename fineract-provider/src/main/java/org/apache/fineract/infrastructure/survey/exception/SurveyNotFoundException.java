package org.apache.fineract.infrastructure.survey.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;

/**
 * A {@link RuntimeException} thrown when survey resources are not found.
 */
public class SurveyNotFoundException extends AbstractPlatformResourceNotFoundException {

    public SurveyNotFoundException(final String name) {
        super("error.msg.survey.name.invalid", "Survey with name " + name + " does not exist", name);
    }

    public SurveyNotFoundException(final String name, EmptyResultDataAccessException e) {
        super("error.msg.survey.name.invalid", "Survey with name " + name + " does not exist", name, e);
    }
} 
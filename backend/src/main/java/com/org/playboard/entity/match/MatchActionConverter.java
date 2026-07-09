package com.org.playboard.entity.match;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MatchActionConverter implements AttributeConverter<MatchAction, String> {

    @Override
    public String convertToDatabaseColumn(MatchAction attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MatchAction convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MatchAction.valueOf(dbData.toUpperCase());
    }
}

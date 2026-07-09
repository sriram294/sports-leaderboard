package com.org.playboard.entity.group;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GroupRoleConverter implements AttributeConverter<GroupRole, String> {

    @Override
    public String convertToDatabaseColumn(GroupRole attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public GroupRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GroupRole.valueOf(dbData.toUpperCase());
    }
}

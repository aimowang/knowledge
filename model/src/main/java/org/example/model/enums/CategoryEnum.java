package org.example.model.enums;

public enum CategoryEnum {
    BASIC("basic"),
    PDF("pdf"),
    MARKDOWN("markdown"),
    CODE("code"),
    ALL("all");

    private final String value;
    CategoryEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

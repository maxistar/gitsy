package me.maxistar.gitsy;

public enum CloneFailureCategory {
    NONE("none"),
    AUTHENTICATION("authentication"),
    NETWORK("network"),
    REMOTE("remote"),
    TLS("tls"),
    LOCAL("local"),
    UNKNOWN("unknown");

    private final String serializedValue;

    CloneFailureCategory(String serializedValue) {
        this.serializedValue = serializedValue;
    }

    public String getSerializedValue() {
        return serializedValue;
    }

    public static CloneFailureCategory fromSerializedValue(String value) {
        if (value == null) return NONE;
        for (CloneFailureCategory category : values()) {
            if (category.serializedValue.equals(value)) return category;
        }
        return NONE;
    }
}

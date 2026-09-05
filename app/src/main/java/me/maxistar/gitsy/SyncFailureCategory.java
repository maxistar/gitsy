package me.maxistar.gitsy;

public enum SyncFailureCategory {
    NONE("none", false),
    TRANSIENT("transient", false),
    AUTHENTICATION("authentication", true),
    SSH_ATTENTION("ssh_attention", true),
    FOLDER_ACCESS("folder_access", true),
    STORAGE("storage", true),
    UNKNOWN("unknown", true);

    private final String serializedValue;
    private final boolean requiresAttention;

    SyncFailureCategory(String serializedValue, boolean requiresAttention) {
        this.serializedValue = serializedValue;
        this.requiresAttention = requiresAttention;
    }

    String getSerializedValue() { return serializedValue; }
    public boolean requiresAttention() { return requiresAttention; }

    static SyncFailureCategory fromSerializedValue(String value) {
        for (SyncFailureCategory category : values()) {
            if (category.serializedValue.equals(value)) return category;
        }
        return NONE;
    }
}

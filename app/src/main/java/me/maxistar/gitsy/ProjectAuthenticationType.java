package me.maxistar.gitsy;

public enum ProjectAuthenticationType {
    HTTPS("https"),
    SSH_KEY("ssh_key");

    private final String serializedValue;

    ProjectAuthenticationType(String serializedValue) {
        this.serializedValue = serializedValue;
    }

    public String getSerializedValue() {
        return serializedValue;
    }

    public static ProjectAuthenticationType fromSerializedValue(String value) {
        for (ProjectAuthenticationType type : values()) {
            if (type.serializedValue.equals(value)) return type;
        }
        throw new IllegalArgumentException("Unsupported project authentication type");
    }
}

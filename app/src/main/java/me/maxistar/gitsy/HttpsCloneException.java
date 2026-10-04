package me.maxistar.gitsy;

final class HttpsCloneException extends Exception {
    private final CloneFailureCategory category;

    HttpsCloneException(CloneFailureCategory category, Throwable cause) {
        super("HTTPS clone failed (" + category.getSerializedValue() + ")", cause);
        this.category = category;
    }

    CloneFailureCategory getCategory() {
        return category;
    }
}

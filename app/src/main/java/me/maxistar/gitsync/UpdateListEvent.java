package me.maxistar.gitsync;

public class UpdateListEvent {
    private final String message;

    public UpdateListEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
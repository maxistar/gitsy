package me.maxistar.gitsy;

final class SafAccessException extends Exception {
    SafAccessException(Throwable cause) { super("Selected folder is unavailable", cause); }
}

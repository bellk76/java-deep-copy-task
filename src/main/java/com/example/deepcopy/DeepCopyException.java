package com.example.deepcopy;

/**
 * Thrown when a deep copy cannot be performed
 * (for example, when an instance of a class cannot be created).
 */
public class DeepCopyException extends RuntimeException {

    public DeepCopyException(String message) {
        super(message);
    }

    public DeepCopyException(String message, Throwable cause) {
        super(message, cause);
    }
}

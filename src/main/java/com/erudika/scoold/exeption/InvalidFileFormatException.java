package com.erudika.scoold.exeption;

public class InvalidFileFormatException extends RuntimeException {

    public InvalidFileFormatException() {
        super();
    }

    public InvalidFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }

}

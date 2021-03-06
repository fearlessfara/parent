package com.bok.parent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UserException extends RuntimeException {
    public enum UserExceptionCode {
        USERNAME_ALREADY_EXISTS,
        EMAIL_ALREADY_EXISTS,
        USER_NOT_FOUND,
        USER_NOT_ENABLED,
        INVALID_CREDENTIALS,
        USER_NOT_EXISTS,
        BAD_ID_PROVIDED
    }

    public UserException(UserExceptionCode userExceptionCode) {
        super(String.valueOf(userExceptionCode));
    }
}

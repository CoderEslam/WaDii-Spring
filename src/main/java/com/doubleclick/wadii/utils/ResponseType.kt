package com.doubleclick.wadii.utils

enum class ResponseType(val code: Int) {
    SUCCESS(200),
    ERROR(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500)
}
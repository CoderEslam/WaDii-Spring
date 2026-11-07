package com.doubleclick.wadii.common.error;

import org.springframework.http.HttpStatus;

/** Stable business error codes returned in {@code ApiResponse.code}. Clients branch on these, never on messages. */
public enum ErrorCode {

    // ---- generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request is invalid."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body or parameters could not be read."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The token has expired, please log in again."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The token is invalid."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to perform this action."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account is suspended."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported here."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was changed by another request, please retry."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please wait and try again."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error."),

    // ---- identity
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "An account already exists with this email."),
    PHONE_ALREADY_USED(HttpStatus.CONFLICT, "An account already exists with this phone number."),

    // ---- files
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "A file is required."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "This file type is not allowed."),
    FILE_CONTENT_MISMATCH(HttpStatus.BAD_REQUEST, "The file content does not match its type."),

    // ---- provider
    PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Provider not found."),
    PROVIDER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Provider mode is not active for this account."),
    PROVIDER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "The provider cannot receive this request."),
    APPLICATION_NOT_EDITABLE(HttpStatus.CONFLICT, "The application can no longer be edited."),
    APPLICATION_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Add at least one service and one document before submitting."),
    APPLICATION_INVALID_STATE(HttpStatus.CONFLICT, "The application is not in a state that allows this action."),
    SUBSCRIPTION_RESTRICTED(HttpStatus.FORBIDDEN, "Provider activity is restricted until the subscription is settled."),

    // ---- catalog
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Service category not found."),
    CONFIG_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "The configuration is invalid."),

    // ---- requests
    VEHICLE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Vehicle details are required for the selected services."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Request not found."),
    REQUEST_NOT_EDITABLE(HttpStatus.CONFLICT, "The request already has offers and can no longer be edited."),
    REQUEST_DEADLINE_PASSED(HttpStatus.CONFLICT, "The response deadline for this request has passed."),
    ITEM_NOT_OPEN(HttpStatus.CONFLICT, "This service item is no longer open."),

    // ---- offers
    NOT_TARGETED(HttpStatus.FORBIDDEN, "This request was not sent to you."),
    CATEGORY_NOT_OFFERED(HttpStatus.UNPROCESSABLE_ENTITY, "You do not offer this service."),
    OFFER_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already have an active offer on this request."),
    OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Offer not found."),
    OFFER_LOCKED(HttpStatus.CONFLICT, "Part of this offer was accepted, so it can no longer be changed."),
    OFFER_NOT_PENDING(HttpStatus.CONFLICT, "Only a pending offer line can be accepted or declined."),
    OFFER_EXPIRED(HttpStatus.CONFLICT, "This offer has expired."),
    INVALID_EXPIRY(HttpStatus.UNPROCESSABLE_ENTITY, "The offer validity is outside the allowed range."),
    SELF_DEALING_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY, "You cannot accept an offer from your own provider account."),

    // ---- appointments
    OUTSIDE_WORKING_HOURS(HttpStatus.UNPROCESSABLE_ENTITY, "The appointment is outside the branch working hours."),
    SLOT_FULL(HttpStatus.CONFLICT, "The branch has no capacity left at this time."),
    APPOINTMENT_IN_PAST(HttpStatus.UNPROCESSABLE_ENTITY, "The appointment time is in the past."),

    // ---- orders
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found."),
    ORDER_INVALID_STATE(HttpStatus.CONFLICT, "The order is not in a state that allows this action."),
    NO_SHOW_TOO_EARLY(HttpStatus.CONFLICT, "A no-show can be reported only after the appointment grace period."),
    CASH_INVALID_STATE(HttpStatus.CONFLICT, "The cash record is not in a state that allows this action."),
    RATING_NOT_ALLOWED(HttpStatus.CONFLICT, "You can rate only after confirming completion."),
    RATING_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already rated this order."),

    // ---- warranty / disputes
    WARRANTY_NOT_FOUND(HttpStatus.NOT_FOUND, "Warranty not found."),
    WARRANTY_NOT_ACTIVE(HttpStatus.CONFLICT, "The warranty is not active."),
    CLAIM_INVALID_STATE(HttpStatus.CONFLICT, "The warranty claim is not in a state that allows this action."),
    DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Dispute not found."),
    DISPUTE_INVALID_STATE(HttpStatus.CONFLICT, "The dispute is not in a state that allows this action."),
    DISPUTE_ALREADY_OPEN(HttpStatus.CONFLICT, "An open dispute of this type already exists."),

    // ---- listings / finance / chat
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "Listing not found."),
    LISTING_INVALID_STATE(HttpStatus.CONFLICT, "The listing is not in a state that allows this action."),
    LISTING_LIMIT_REACHED(HttpStatus.CONFLICT, "You reached the maximum number of active listings."),
    LEDGER_INVALID_STATE(HttpStatus.CONFLICT, "The ledger entry is not in a state that allows this action."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

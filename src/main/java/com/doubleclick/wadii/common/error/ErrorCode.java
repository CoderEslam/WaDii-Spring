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
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error."),

    // ---- identity
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "An account already exists with this email."),
    ADMIN_REGISTRATION_FORBIDDEN(HttpStatus.FORBIDDEN, "Admin accounts cannot be created through registration."),

    // ---- files
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "A file is required."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "This file type is not allowed."),
    FILE_CONTENT_MISMATCH(HttpStatus.BAD_REQUEST, "The file content does not match its type."),

    // ---- provider
    PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Provider not found."),
    PROVIDER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "The provider cannot receive new orders."),
    PROVIDER_SERVICES_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "The provider does not offer every requested service."),
    PROVIDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "This account already has a provider profile."),
    PAYMENT_ACCOUNT_MISSING(HttpStatus.UNPROCESSABLE_ENTITY, "The provider has no receiving account for this payment method."),

    // ---- order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found."),
    ORDER_INVALID_STATE(HttpStatus.CONFLICT, "The order is not in a state that allows this action."),
    ORDER_DEADLINE_PASSED(HttpStatus.CONFLICT, "The order no longer accepts offers."),
    ORDER_NOT_FULLY_PAID(HttpStatus.CONFLICT, "The order must be fully paid first."),
    SELF_ORDER_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY, "You cannot send an order to your own provider profile."),

    // ---- response / offer
    RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Offer not found."),
    RESPONSE_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already sent an offer for this order."),
    RESPONSE_NOT_TARGETED(HttpStatus.FORBIDDEN, "You were not invited to respond to this order."),
    RESPONSE_NOT_PENDING(HttpStatus.CONFLICT, "Only a pending offer can be changed."),
    OFFER_EXPIRED(HttpStatus.CONFLICT, "This offer has expired."),
    RESPONSE_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "An offer was already accepted for this order."),
    RESPONSE_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "The offer does not belong to this order."),
    INVALID_EXPIRY(HttpStatus.UNPROCESSABLE_ENTITY, "The offer expiry is outside the allowed range."),

    // ---- appointment
    APPOINTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Appointment not found."),
    APPOINTMENT_OUTSIDE_WORKING_HOURS(HttpStatus.UNPROCESSABLE_ENTITY, "The appointment is outside the branch working hours."),
    APPOINTMENT_IN_PAST(HttpStatus.UNPROCESSABLE_ENTITY, "The appointment time is in the past."),
    APPOINTMENT_INVALID_STATE(HttpStatus.CONFLICT, "The appointment is not in a state that allows this action."),
    NO_SHOW_TOO_EARLY(HttpStatus.CONFLICT, "A no-show can only be reported after the appointment time."),

    // ---- policy
    POLICY_VIOLATES_PLATFORM_RULES(HttpStatus.UNPROCESSABLE_ENTITY, "The policy weakens a mandatory platform rule."),

    // ---- payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found."),
    PAYMENT_INVALID_STATE(HttpStatus.CONFLICT, "The payment is not in a state that allows this action."),
    PAYMENT_METHOD_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "This payment method is not supported."),
    PAYMENT_METHOD_REQUIRED(HttpStatus.CONFLICT, "Select a payment method first."),
    PAYMENT_NOTHING_DUE(HttpStatus.CONFLICT, "Nothing is due on this order."),
    PAYMENT_PROOF_LIMIT(HttpStatus.CONFLICT, "Too many proofs were uploaded for this payment."),
    PAYMENT_PROOF_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "This payment method does not use proofs."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Refund not found."),
    REFUND_AMOUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "The refund amount exceeds the refundable amount."),
    REFUND_INVALID_STATE(HttpStatus.CONFLICT, "The refund is not in a state that allows this action."),
    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Additional charge not found."),
    CHARGE_INVALID_STATE(HttpStatus.CONFLICT, "The additional charge is not in a state that allows this action."),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Invoice not found."),

    // ---- dispute / warranty / return
    DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Dispute not found."),
    DISPUTE_INVALID_STATE(HttpStatus.CONFLICT, "The dispute is not in a state that allows this action."),
    DISPUTE_ALREADY_OPEN(HttpStatus.CONFLICT, "An open dispute of this type already exists for this order."),
    WARRANTY_NOT_FOUND(HttpStatus.NOT_FOUND, "Warranty not found."),
    WARRANTY_EXPIRED(HttpStatus.CONFLICT, "The warranty period is over."),
    WARRANTY_CLAIM_INVALID_STATE(HttpStatus.CONFLICT, "The warranty claim is not in a state that allows this action."),
    RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "Return request not found."),
    RETURN_INVALID_STATE(HttpStatus.CONFLICT, "The return request is not in a state that allows this action."),
    RETURN_WINDOW_CLOSED(HttpStatus.CONFLICT, "The return period is over."),

    // ---- rating / social
    RATING_NOT_ALLOWED(HttpStatus.CONFLICT, "You can rate a provider only after the order is completed."),
    RATING_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already rated this order."),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "You already follow this provider."),
    OFFER_ALREADY_SAVED(HttpStatus.CONFLICT, "This offer is already saved."),

    // ---- chat / calls
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found."),
    CONVERSATION_CLOSED(HttpStatus.CONFLICT, "This conversation is closed.");

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

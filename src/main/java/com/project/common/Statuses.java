package com.project.common;

public final class Statuses {

    private Statuses() {
    }

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String TYPE_LOST = "LOST";
    public static final String TYPE_FOUND = "FOUND";

    public static final String ITEM_PENDING = "PENDING";
    public static final String ITEM_PUBLIC = "PUBLIC";
    public static final String ITEM_REJECTED = "REJECTED";
    public static final String ITEM_CLOSED = "CLOSED";
    public static final String ITEM_CLAIMED = "CLAIMED";

    public static final String CLAIM_PENDING = "PENDING";
    public static final String CLAIM_APPROVED = "APPROVED";
    public static final String CLAIM_REJECTED = "REJECTED";
    public static final String CLAIM_CANCELLED = "CANCELLED";
    public static final String CLAIM_HANDED = "HANDED";
    public static final String CLAIM_REVOKED = "REVOKED";

    public static final String LOG_ITEM_REVIEW = "ITEM_REVIEW";
    public static final String LOG_ITEM_CLOSE = "ITEM_CLOSE";
    public static final String LOG_CLAIM_SUBMIT = "CLAIM_SUBMIT";
    public static final String LOG_CLAIM_CANCEL = "CLAIM_CANCEL";
    public static final String LOG_CLAIM_REVIEW = "CLAIM_REVIEW";
    public static final String LOG_CLAIM_REVOKE = "CLAIM_REVOKE";
    public static final String LOG_CLAIM_HANDOVER = "CLAIM_HANDOVER";

    public static boolean isAdmin(String role) {
        return ROLE_ADMIN.equals(role);
    }

    public static boolean isLost(String type) {
        return TYPE_LOST.equals(type);
    }

    public static boolean isFound(String type) {
        return TYPE_FOUND.equals(type);
    }
}

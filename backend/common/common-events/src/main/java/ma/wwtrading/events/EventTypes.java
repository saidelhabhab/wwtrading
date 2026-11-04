package ma.wwtrading.events;

/** Stable event type and version identifiers. */
public final class EventTypes {
    public static final String VERSION_1 = "v1";

    public static final String USER_CREATED = "USER_CREATED";
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    public static final String QUOTATION_CREATED = "QUOTATION_CREATED";
    public static final String QUOTATION_STATUS_CHANGED = "QUOTATION_STATUS_CHANGED";
    public static final String MAINTENANCE_CREATED = "MAINTENANCE_CREATED";
    public static final String MAINTENANCE_ASSIGNED = "MAINTENANCE_ASSIGNED";
    public static final String MAINTENANCE_STATUS_CHANGED = "MAINTENANCE_STATUS_CHANGED";
    public static final String MAINTENANCE_COMPLETED = "MAINTENANCE_COMPLETED";
    public static final String SUPPORT_MESSAGE_CREATED = "SUPPORT_MESSAGE_CREATED";
    public static final String SUPPORT_REPLY_CREATED = "SUPPORT_REPLY_CREATED";

    private EventTypes() {
    }
}

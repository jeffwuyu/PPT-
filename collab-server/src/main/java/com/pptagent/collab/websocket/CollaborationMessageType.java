package com.pptagent.collab.websocket;

public final class CollaborationMessageType {

    public static final String JOIN = "join";
    public static final String CURSOR = "cursor";
    public static final String YJS_UPDATE = "yjs-update";
    public static final String YJS_ACK = "yjs-ack";
    public static final String SYNC_REQUEST = "sync-request";
    public static final String SYNC_STATE = "sync-state";
    public static final String PRESENCE = "presence";
    public static final String LOCK_ACQUIRE = "lock-acquire";
    public static final String LOCK_RELEASE = "lock-release";
    public static final String LOCK_GRANTED = "lock-granted";
    public static final String LOCK_DENIED = "lock-denied";
    public static final String LOCK_RELEASED = "lock-released";
    public static final String ERROR = "error";

    private CollaborationMessageType() {
    }
}

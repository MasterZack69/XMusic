package com.xapps.media.xmusic.callback;

public final class CallbackInterface {

    private static ActivityCallback activityCallback;
    private static ServiceCallback serviceCallback;

    private CallbackInterface() {
    }

    public static void setActivityCallback(ActivityCallback callback) {
        activityCallback = callback;
    }

    public static void clearActivityCallback(ActivityCallback callback) {
        if (activityCallback == callback) {
            activityCallback = null;
        }
    }

    public static ActivityCallback activity() {
        return activityCallback;
    }

    public static void setServiceCallback(ServiceCallback callback) {
        serviceCallback = callback;
    }

    public static void clearServiceCallback(ServiceCallback callback) {
        if (serviceCallback == callback) {
            serviceCallback = null;
        }
    }

    public static ServiceCallback service() {
        return serviceCallback;
    }
}
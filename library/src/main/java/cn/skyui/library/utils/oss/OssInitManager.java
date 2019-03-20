package cn.skyui.library.utils.oss;

import android.app.Application;

public class OssInitManager {

    private static boolean isInitialized = false;

    private static Application application;
    private static ITokenProvider tokenProvider;

    public static void init(Application _application, ITokenProvider _tokenProvider) {
        application = _application;
        tokenProvider = _tokenProvider;
        isInitialized = true;
    }

    public static boolean isIsInitialized() {
        return isInitialized;
    }

    public static Application getApplication() {
        return application;
    }

    public static ITokenProvider getTokenProvider() {
        return tokenProvider;
    }
}

package me.maxistar.gitsync;

public class ServiceLocator {
    private static ServiceLocator instance = null;

    private ServiceLocator() {}

    private WakeLockService wakeLockService = null;

    public static ServiceLocator getInstance() {
        if (instance == null) {
            synchronized(ServiceLocator.class) {
                instance = new ServiceLocator();
            }
        }
        return instance;
    }

    public WakeLockService getWakeLockService() {
        if (wakeLockService == null) {
            wakeLockService = new WakeLockService();
        }
        return wakeLockService;
    }
}

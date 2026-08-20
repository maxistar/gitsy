package me.maxistar.gitsy;

public class ServiceLocator {
    private static ServiceLocator instance = null;

    private ServiceLocator() {}

    private WakeLockService wakeLockService = null;

    private ValueEncryptor valueEncryptor = null;

    private StartupSyncSession startupSyncSession = new StartupSyncSession();

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

    public ValueEncryptor getValueEncryptor() throws Exception {
        if (valueEncryptor == null) {
            valueEncryptor = new ValueEncryptor();
            valueEncryptor.ensureKey();
        }
        return valueEncryptor;
    }

    public StartupSyncSession getStartupSyncSession() {
        return startupSyncSession;
    }

    void replaceStartupSyncSessionForTests(StartupSyncSession startupSyncSession) {
        this.startupSyncSession = startupSyncSession;
    }
}

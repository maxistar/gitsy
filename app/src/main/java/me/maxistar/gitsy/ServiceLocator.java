package me.maxistar.gitsy;

public class ServiceLocator {
    private static ServiceLocator instance = null;

    private ServiceLocator() {}

    private WakeLockService wakeLockService = null;

    private ValueEncryptor valueEncryptor = null;

    private StartupSyncSession startupSyncSession = new StartupSyncSession();
    private SshIdentityMaterialLoader sshIdentityMaterialLoader;
    private SshHostTrustCoordinator sshHostTrustCoordinator;
    private SshOperationAttentionEvent pendingSshAttention;

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

    public synchronized ProjectTransportAuthenticationFactory getTransportAuthenticationFactory(
            android.content.Context context) throws Exception {
        android.content.Context application = context.getApplicationContext();
        if (sshIdentityMaterialLoader == null) {
            ValueEncryptor encryptor = getValueEncryptor();
            sshIdentityMaterialLoader = new SshIdentityMaterialLoader(
                    new EncryptedFileSshIdentityRepository(application, encryptor),
                    new EncryptedFileSshPassphraseRepository(application, encryptor));
        }
        ensureSshHostTrust(application);
        return new ProjectTransportAuthenticationFactory(
                sshIdentityMaterialLoader, sshHostTrustCoordinator);
    }

    private void ensureSshHostTrust(android.content.Context context) {
        if (sshHostTrustCoordinator == null) {
            sshHostTrustCoordinator = new SshHostTrustCoordinator(new FileTrustedHostRepository(context));
        }
    }

    public synchronized boolean acceptSshHost(android.content.Context context, String token)
            throws Exception {
        ensureSshHostTrust(context.getApplicationContext());
        return sshHostTrustCoordinator.accept(token);
    }

    public synchronized boolean declineSshHost(android.content.Context context, String token) {
        ensureSshHostTrust(context.getApplicationContext());
        return sshHostTrustCoordinator.decline(token);
    }

    public synchronized void publishSshAttention(SshOperationAttentionEvent event) {
        pendingSshAttention = event;
        EventBus.getInstance().post(event);
    }

    public synchronized SshOperationAttentionEvent getPendingSshAttention() {
        return pendingSshAttention;
    }

    public synchronized void clearPendingSshAttention(SshOperationAttentionEvent event) {
        if (pendingSshAttention == event) pendingSshAttention = null;
    }

    void replaceStartupSyncSessionForTests(StartupSyncSession startupSyncSession) {
        this.startupSyncSession = startupSyncSession;
    }

    synchronized void replaceSshHostTrustCoordinatorForTests(
            SshHostTrustCoordinator sshHostTrustCoordinator) {
        this.sshHostTrustCoordinator = sshHostTrustCoordinator;
    }
}

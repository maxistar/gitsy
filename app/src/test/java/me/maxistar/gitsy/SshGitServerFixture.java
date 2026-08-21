package me.maxistar.gitsy;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SshGitServerFixture implements AutoCloseable {
    private final Repository repository;
    private final Path hostKey;
    private SshServer server;

    SshGitServerFixture(Repository repository, Path directory) {
        this.repository = repository;
        this.hostKey = directory.resolve("ssh-host-key");
    }

    void start() throws IOException {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider hostKeys = new SimpleGeneratorHostKeyProvider(hostKey);
        hostKeys.setAlgorithm("EC");
        server.setKeyPairProvider(hostKeys);
        server.setPublickeyAuthenticator((username, key, session) -> true);
        server.setCommandFactory((channel, command) -> new GitPackCommand(repository, command));
        server.start();
    }

    int port() {
        return server.getPort();
    }

    void rotateHostKey() throws IOException {
        close();
        Files.deleteIfExists(hostKey);
        start();
    }

    @Override
    public void close() throws IOException {
        if (server != null) {
            server.stop(true);
            server = null;
        }
    }

    private static final class GitPackCommand implements Command, Runnable {
        private final Repository repository;
        private final String command;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private InputStream input;
        private OutputStream output;
        private OutputStream error;
        private ExitCallback exitCallback;

        GitPackCommand(Repository repository, String command) {
            this.repository = repository;
            this.command = command;
        }

        @Override public void setInputStream(InputStream input) { this.input = input; }
        @Override public void setOutputStream(OutputStream output) { this.output = output; }
        @Override public void setErrorStream(OutputStream error) { this.error = error; }
        @Override public void setExitCallback(ExitCallback callback) { this.exitCallback = callback; }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            executor.execute(this);
        }

        @Override
        public void destroy(ChannelSession channel) {
            executor.shutdownNow();
        }

        @Override
        public void run() {
            int exit = 0;
            try {
                if (command.startsWith("git-upload-pack ")) {
                    new UploadPack(repository).upload(input, output, error);
                } else if (command.startsWith("git-receive-pack ")) {
                    new ReceivePack(repository).receive(input, output, error);
                } else {
                    exit = 127;
                }
            } catch (Exception exception) {
                exit = 1;
                try {
                    error.write(exception.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // The client may already have disconnected.
                }
            } finally {
                exitCallback.onExit(exit);
                executor.shutdown();
            }
        }
    }
}

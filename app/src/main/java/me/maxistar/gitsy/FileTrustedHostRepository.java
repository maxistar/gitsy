package me.maxistar.gitsy;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class FileTrustedHostRepository implements TrustedHostRepository {
    private static final String FILE_NAME = "ssh-trusted-hosts.json";
    private static final Type RECORD_LIST = new TypeToken<ArrayList<Record>>() { }.getType();

    private final File file;
    private final Gson gson;

    public FileTrustedHostRepository(Context context) {
        this(new File(context.getFilesDir(), FILE_NAME), new Gson());
    }

    FileTrustedHostRepository(File file, Gson gson) {
        this.file = Objects.requireNonNull(file);
        this.gson = Objects.requireNonNull(gson);
    }

    @Override public synchronized TrustedHostMatch check(SshHostIdentity candidate) throws Exception {
        for (Record record : read()) {
            SshHostIdentity trusted = record.toIdentity();
            if (trusted.sameEndpoint(candidate)) {
                return trusted.sameKey(candidate) ? TrustedHostMatch.MATCH : TrustedHostMatch.CHANGED;
            }
        }
        return TrustedHostMatch.UNKNOWN;
    }

    @Override public synchronized void trustUnknown(SshHostIdentity candidate) throws Exception {
        TrustedHostMatch match = check(candidate);
        if (match == TrustedHostMatch.CHANGED) {
            throw new IllegalStateException("SSH host key changed");
        }
        if (match == TrustedHostMatch.MATCH) return;
        List<Record> records = read();
        records.add(Record.from(candidate));
        write(records);
    }

    @Override public synchronized void replace(SshHostIdentity candidate) throws Exception {
        List<Record> records = read();
        removeEndpoint(records, candidate.getHost(), candidate.getPort());
        records.add(Record.from(candidate));
        write(records);
    }

    @Override public synchronized void remove(String host, int port) throws Exception {
        List<Record> records = read();
        if (removeEndpoint(records, host, port)) write(records);
    }

    private List<Record> read() throws IOException {
        if (!file.isFile()) return new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            List<Record> records = gson.fromJson(reader, RECORD_LIST);
            if (records == null) throw new IOException("Trusted host store is corrupt");
            return records;
        } catch (RuntimeException error) {
            throw new IOException("Trusted host store is corrupt", error);
        }
    }

    private void write(List<Record> records) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create trusted host store");
        }
        File staging = new File(parent, file.getName() + ".staging");
        try (FileWriter writer = new FileWriter(staging)) {
            gson.toJson(records, RECORD_LIST, writer);
            writer.flush();
        }
        if (file.exists() && !file.delete()) {
            staging.delete();
            throw new IOException("Cannot replace trusted host store");
        }
        if (!staging.renameTo(file)) {
            staging.delete();
            throw new IOException("Cannot commit trusted host store");
        }
    }

    private static boolean removeEndpoint(List<Record> records, String host, int port) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return records.removeIf(record -> record.port == port && normalized.equals(record.host));
    }

    private static final class Record {
        String host;
        int port;
        String algorithm;
        byte[] key;

        static Record from(SshHostIdentity identity) {
            Record record = new Record();
            record.host = identity.getHost();
            record.port = identity.getPort();
            record.algorithm = identity.getAlgorithm();
            record.key = identity.getKey();
            return record;
        }

        SshHostIdentity toIdentity() { return new SshHostIdentity(host, port, algorithm, key); }
    }
}

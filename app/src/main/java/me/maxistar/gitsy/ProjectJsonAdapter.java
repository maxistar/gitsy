package me.maxistar.gitsy;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class ProjectJsonAdapter implements JsonSerializer<ProjectModel>, JsonDeserializer<ProjectModel>  {
    interface CredentialCodec {
        String encrypt(String value, String associatedData) throws Exception;
        String decrypt(String value, String associatedData) throws Exception;
    }

    private final CredentialCodec credentialCodec;

    public ProjectJsonAdapter() {
        this(new CredentialCodec() {
            @Override
            public String encrypt(String value, String associatedData) throws Exception {
                return ServiceLocator.getInstance().getValueEncryptor().encryptValue(value, associatedData);
            }

            @Override
            public String decrypt(String value, String associatedData) throws Exception {
                return ServiceLocator.getInstance().getValueEncryptor().decryptValue(value, associatedData);
            }
        });
    }

    ProjectJsonAdapter(CredentialCodec credentialCodec) {
        this.credentialCodec = credentialCodec;
    }

    @Override
    public JsonElement serialize(ProjectModel src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("folderName", src.folderName);
        obj.addProperty("folderUri", src.folderUri);
        obj.addProperty("repoUrl", src.repoUrl);
        obj.addProperty("userName", src.userName);
        obj.addProperty("authenticationType", src.authenticationType.getSerializedValue());
        if (src.authenticationType == ProjectAuthenticationType.SSH_KEY) {
            obj.addProperty("sshPort", src.sshPort);
        }
        obj.addProperty("lastSync", src.lastSync);
        obj.addProperty("numberFiles", src.numberFiles);
        obj.addProperty("status", src.status);
        if (src.cloneFailureCategory != CloneFailureCategory.NONE) {
            obj.addProperty("cloneFailureCategory",
                    src.cloneFailureCategory.getSerializedValue());
        }
        if (src.syncFailureCategory != SyncFailureCategory.NONE) {
            obj.addProperty("syncFailureCategory", src.syncFailureCategory.getSerializedValue());
        }
        //obj.addProperty("password", src.password);

        try {
            if (src.authenticationType == ProjectAuthenticationType.HTTPS && src.password != null) {
                String enc = credentialCodec.encrypt(src.password, src.userName);
                obj.addProperty("password", enc);
            } else {
                obj.add("password", JsonNull.INSTANCE);
            }
        } catch (Exception e) {
            throw new JsonIOException("Failed to encrypt password", e);
        }
        return obj;
    }

    @Override
    public ProjectModel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        ProjectModel p = new ProjectModel(
                obj.get("repoUrl").getAsString(),
                obj.get("userName").getAsString(),
                "", //obj.get("password").getAsString(),
                obj.get("folderUri").getAsString()
        );

        if (obj.has("authenticationType") && !obj.get("authenticationType").isJsonNull()) {
            try {
                p.authenticationType = ProjectAuthenticationType.fromSerializedValue(
                        obj.get("authenticationType").getAsString());
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Unsupported project authentication type", exception);
            }
        }
        if (p.authenticationType == ProjectAuthenticationType.SSH_KEY) {
            p.sshPort = intValue(obj, "sshPort", 22);
            p.password = null;
        }

        if (obj.has("folderName") && !obj.get("folderName").isJsonNull()) {
            p.folderName = obj.get("folderName").getAsString();
        }
        p.lastSync = longValue(obj, "lastSync", 0L);
        p.numberFiles = intValue(obj, "numberFiles", 0);
        p.status = intValue(obj, "status", ProjectModel.STATUS_TO_CLONE);
        p.cloneFailureCategory = obj.has("cloneFailureCategory")
                && !obj.get("cloneFailureCategory").isJsonNull()
                ? CloneFailureCategory.fromSerializedValue(
                        obj.get("cloneFailureCategory").getAsString())
                : CloneFailureCategory.NONE;
        p.syncFailureCategory = obj.has("syncFailureCategory")
                && !obj.get("syncFailureCategory").isJsonNull()
                ? SyncFailureCategory.fromSerializedValue(
                        obj.get("syncFailureCategory").getAsString())
                : SyncFailureCategory.NONE;

        if (p.authenticationType == ProjectAuthenticationType.HTTPS
                && obj.has("password") && !obj.get("password").isJsonNull()) {
            String blob = obj.get("password").getAsString();
            try {
                p.password = credentialCodec.decrypt(blob, p.userName);
            } catch (Exception e) {
                throw new JsonParseException("Failed to decrypt password", e);
            }
        }
        return p;
    }

    private static long longValue(JsonObject obj, String name, long defaultValue) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsLong() : defaultValue;
    }

    private static int intValue(JsonObject obj, String name, int defaultValue) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsInt() : defaultValue;
    }

}

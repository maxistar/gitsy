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
        obj.addProperty("lastSync", src.lastSync);
        obj.addProperty("numberFiles", src.numberFiles);
        obj.addProperty("status", src.status);
        //obj.addProperty("password", src.password);

        try {
            if (src.password != null) {
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

        if (obj.has("folderName") && !obj.get("folderName").isJsonNull()) {
            p.folderName = obj.get("folderName").getAsString();
        }
        p.lastSync = longValue(obj, "lastSync", 0L);
        p.numberFiles = intValue(obj, "numberFiles", 0);
        p.status = intValue(obj, "status", ProjectModel.STATUS_TO_CLONE);

        if (obj.has("password") && !obj.get("password").isJsonNull()) {
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

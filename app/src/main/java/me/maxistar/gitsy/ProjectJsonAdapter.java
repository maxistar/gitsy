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
                String enc = ServiceLocator.getInstance().getValueEncryptor().encryptValue(src.password, src.userName); // AAD = userName
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

        p.folderName = obj.get("folderName").getAsString();
        p.lastSync = obj.get("lastSync").getAsLong();
        p.numberFiles = obj.get("numberFiles").getAsInt();
        p.status = obj.get("status").getAsInt();

        if (obj.has("password") && !obj.get("password").isJsonNull()) {
            String blob = obj.get("password").getAsString();
            try {
                p.password = ServiceLocator.getInstance().getValueEncryptor().decryptValue(blob, p.userName); // AAD = userName
            } catch (Exception e) {
                throw new JsonParseException("Failed to decrypt password", e);
            }
        }
        return p;
    }

}

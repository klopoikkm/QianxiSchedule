package com.qianxi.schedule.importer;

import org.json.JSONException;
import org.json.JSONObject;

public final class SchoolProfile {
    public static final String CUSTOM_ENTRY_ID = "custom-entry";

    public final String id;
    public final String name;
    public final String url;
    public final String adapterId;
    public final boolean custom;

    public SchoolProfile(String id, String name, String url, String adapterId, boolean custom) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.adapterId = adapterId;
        this.custom = custom;
    }

    public static SchoolProfile customEntry() {
        return new SchoolProfile(CUSTOM_ENTRY_ID, "输入或选择教务网址", "", ImportAdapter.AUTO, false);
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("url", url)
                .put("adapterId", adapterId);
    }

    public static SchoolProfile fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        String name = json.optString("name", "").trim();
        String url = json.optString("url", "").trim();
        String adapter = json.optString("adapterId", ImportAdapter.AUTO);
        if (id.isEmpty() || name.isEmpty() || url.isEmpty()) return null;
        return new SchoolProfile(id, name, url, adapter, true);
    }

    @Override
    public String toString() {
        return name;
    }
}

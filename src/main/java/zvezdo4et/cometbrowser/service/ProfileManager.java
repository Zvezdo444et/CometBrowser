package zvezdo4et.cometbrowser.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zvezdo4et.cometbrowser.model.BrowserProfile;
import zvezdo4et.cometbrowser.util.UserDataDirUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class ProfileManager {

    private static final Logger LOG = Logger.getLogger(ProfileManager.class.getName());
    private static ProfileManager instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, BrowserProfile> profiles = new LinkedHashMap<>();
    private final List<String> profileOrder = new ArrayList<>();
    private final File configFile = new File(
            UserDataDirUtil.getAppDataDir() + File.separator + "profiles.json");

    private static final String[] COMET_COLORS = {
            "#A78BFA", "#60A5FA", "#34D399", "#F472B6",
            "#FBBF24", "#F87171", "#38BDF8", "#818CF8"
    };

    private ProfileManager() {
    }

    public static ProfileManager getInstance() {
        if (instance == null) instance = new ProfileManager();
        return instance;
    }

    public void initialize() {
        loadFromDisk();
        if (profiles.isEmpty()) createProfile("Орбита 1");
    }

    private void loadFromDisk() {
        if (!configFile.exists()) return;
        try {
            List<Map<String, String>> raw = mapper.readValue(configFile, new TypeReference<>() {
            });
            for (Map<String, String> entry : raw) {
                BrowserProfile p = new BrowserProfile(
                        entry.get("id"), entry.get("name"),
                        entry.get("avatarColor"), entry.get("dataDir"));
                new File(p.getDataDir()).mkdirs();
                profiles.put(p.getId(), p);
                profileOrder.add(p.getId());
            }
        } catch (IOException e) {
            LOG.warning("Failed to load profiles: " + e.getMessage());
        }
    }

    public void saveAll() {
        List<Map<String, String>> raw = new ArrayList<>();
        for (String id : profileOrder) {
            BrowserProfile p = profiles.get(id);
            if (p == null) continue;
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", p.getId());
            entry.put("name", p.getName());
            entry.put("avatarColor", p.getAvatarColor());
            entry.put("dataDir", p.getDataDir());
            raw.add(entry);
        }
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, raw);
        } catch (IOException e) {
            LOG.warning("Failed to save profiles: " + e.getMessage());
        }
    }

    public BrowserProfile createProfile(String name) {
        int colorIndex = profiles.size() % COMET_COLORS.length;
        String dataDir = UserDataDirUtil.getAppDataDir() + File.separator
                + "profiles" + File.separator + UUID.randomUUID();
        BrowserProfile profile = new BrowserProfile(name, COMET_COLORS[colorIndex], dataDir);
        new File(dataDir).mkdirs();
        profiles.put(profile.getId(), profile);
        profileOrder.add(profile.getId());
        saveAll();
        return profile;
    }

    public void deleteProfile(String profileId) {
        profiles.remove(profileId);
        profileOrder.remove(profileId);
        saveAll();
    }

    public void renameProfile(String profileId, String newName) {
        BrowserProfile p = profiles.get(profileId);
        if (p != null && newName != null && !newName.isBlank()) {
            p.setName(newName.trim());
            saveAll();
        }
    }

    public BrowserProfile getProfile(String profileId) {
        return profiles.get(profileId);
    }

    public List<BrowserProfile> getAllProfiles() {
        List<BrowserProfile> result = new ArrayList<>();
        for (String id : profileOrder) {
            BrowserProfile p = profiles.get(id);
            if (p != null) result.add(p);
        }
        return result;
    }

    public void reorderProfiles(List<String> orderedIds) {
        profileOrder.clear();
        for (String id : orderedIds) {
            if (profiles.containsKey(id)) profileOrder.add(id);
        }
        saveAll();
    }
}
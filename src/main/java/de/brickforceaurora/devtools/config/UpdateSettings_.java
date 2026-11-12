package de.brickforceaurora.devtools.config;

import me.lauriichan.snowframe.config.Config;
import me.lauriichan.snowframe.config.ConfigValue;
import me.lauriichan.snowframe.config.IConfigHandler;
import me.lauriichan.snowframe.config.ISingleConfigExtension;
import me.lauriichan.snowframe.config.handler.JsonConfigHandler;
import me.lauriichan.snowframe.extension.Extension;

@Config(automatic = true)
@Extension
public class UpdateSettings_ implements ISingleConfigExtension {

    @ConfigValue("game.directory")
    public String gameDirectory;

    @ConfigValue("game.version")
    public String gameVersion;

    @ConfigValue("api.key")
    public String apiKey;
    @ConfigValue("api.host")
    public String apiHost = "https://api.brickforce-aurora.de/updater/v1/publish";

    @Override
    public IConfigHandler handler() {
        return JsonConfigHandler.JSON;
    }

    @Override
    public String path() {
        return "user://updates.json";
    }

}

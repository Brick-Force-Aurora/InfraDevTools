package de.brickforceaurora.devtools.command.api;

import java.util.UUID;

import de.brickforceaurora.devtools.DevApp;
import de.brickforceaurora.devtools.command.message.CommandManagerMessage;
import de.brickforceaurora.devtools.command.message.SimpleMessageProviderFactory;
import de.brickforceaurora.devtools.ui.widget.ImGuiConsole.LogHistory;
import de.brickforceaurora.devtools.ui.widget.ImGuiConsole.LogType;
import me.lauriichan.laylib.command.Actor;
import me.lauriichan.laylib.localization.MessageManager;
import me.lauriichan.laylib.localization.source.EnumMessageSource;
import me.lauriichan.snowframe.SnowFrame;
import me.lauriichan.snowframe.util.logger.FileLogger;

public class ConsoleActor extends Actor<SnowFrame<DevApp>> {

    private static final String COMMAND_LOG_FORMAT = "[%s][COMMAND/%s] %s";

    private final FileLogger fileLogger;
    private final LogHistory history;

    public ConsoleActor(SnowFrame<DevApp> frame) {
        MessageManager messageManager = new MessageManager();
        messageManager.register(new EnumMessageSource(CommandManagerMessage.class, new SimpleMessageProviderFactory()));
        super(frame, messageManager);
        this.fileLogger = (FileLogger) frame.logger();
        this.history = DevApp.LOG_HISTORY;
    }

    @Override
    public UUID getId() {
        return Actor.IMPL_ID;
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public void sendMessage(String message) {
        history.addEntry(LogType.COMMAND, message);
        fileLogger.print(COMMAND_LOG_FORMAT, message);
    }

}

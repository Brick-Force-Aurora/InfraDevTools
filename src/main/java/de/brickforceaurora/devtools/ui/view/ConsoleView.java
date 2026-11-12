package de.brickforceaurora.devtools.ui.view;

import de.brickforceaurora.devtools.DevApp;
import de.brickforceaurora.devtools.FontAtlas;
import de.brickforceaurora.devtools.command.api.CommandHandler;
import de.brickforceaurora.devtools.ui.dock.DockUIExtension;
import de.brickforceaurora.devtools.ui.dock.ImGuiDock;
import de.brickforceaurora.devtools.ui.widget.ImGuiConsole;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.util.instance.Shared;

@Shared
@Extension
public class ConsoleView extends DockUIExtension {

    private final ImGuiConsole console = new ImGuiConsole();
    private final ImVec2 padding = new ImVec2(0, 2);

    public ConsoleView() {
        super("Console", -1, "main", "main");
        console.font(FontAtlas.CONSOLE_FONT);
        console.fontSize(16f);
        console.logHistory(DevApp.LOG_HISTORY);
        console.commandHandler(CommandHandler.HANDLER);
    }

    @Override
    protected void renderContent(ImGuiDock dock, long windowHandle) {
        console.render();
    }
    
    @Override
    protected void pushWindowStyle() {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, padding);
    }
    
    @Override
    protected int windowFlags() {
        return super.windowFlags() | ImGuiWindowFlags.NoScrollbar;
    }
    
    @Override
    protected void popWindowStyle() {
        ImGui.popStyleVar(1);
    }

}

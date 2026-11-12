package de.brickforceaurora.devtools.ui.dock;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import me.lauriichan.snowframe.extension.ExtensionPoint;
import me.lauriichan.snowframe.extension.IExtension;

@ExtensionPoint
public abstract class DockUIExtension implements IExtension {

    private final String title;
    private final String dockId, targetId;
    private final int sortPriority;
    
    private volatile boolean visibleLastFrame = false;

    public DockUIExtension(String title, String dockId, String targetId) {
        this(title, 0, dockId, targetId);
    }

    public DockUIExtension(String title, int sortPriority, String dockId, String targetId) {
        this.title = title;
        this.sortPriority = sortPriority;
        this.dockId = dockId;
        this.targetId = targetId;
    }

    public final String title() {
        return title;
    }

    public final String dockId() {
        return dockId;
    }

    public final String targetId() {
        return targetId;
    }
    
    public final int sortPriority() {
        return sortPriority;
    }
    
    public final boolean isVisible() {
        return visibleLastFrame;
    }

    public final void render(ImGuiDock dock, long windowHandle) {
        pushWindowStyle();
        if (visibleLastFrame = ImGui.begin(title, windowFlags())) {
            renderContent(dock, windowHandle);
        }
        ImGui.end();
        popWindowStyle();
    }

    protected int windowFlags() {
        return ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse;
    }
    
    protected void pushWindowStyle() {
    }

    protected abstract void renderContent(ImGuiDock dock, long windowHandle);

    protected void popWindowStyle() {
    }

    protected void onDockSetup() {}
    
}

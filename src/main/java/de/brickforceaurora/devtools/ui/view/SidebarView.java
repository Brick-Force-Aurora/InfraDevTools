package de.brickforceaurora.devtools.ui.view;

import de.brickforceaurora.devtools.ui.dock.DockUIExtension;
import de.brickforceaurora.devtools.ui.dock.ImGuiDock;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.util.instance.Shared;

@Shared
@Extension
public class SidebarView extends DockUIExtension {

    public static interface ISidebarView {

        void renderSidebar(SidebarView view, ImGuiDock dock, long windowHandle);

    }

    public SidebarView() {
        super("Sidebar", "main", "sidebar");
    }

    @Override
    protected void renderContent(ImGuiDock dock, long windowHandle) {
        if (dock.visibleDockOn("main") instanceof ISidebarView view) {
            view.renderSidebar(this, dock, windowHandle);
        }
    }

}

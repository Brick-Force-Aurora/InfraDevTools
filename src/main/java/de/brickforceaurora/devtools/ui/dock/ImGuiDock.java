package de.brickforceaurora.devtools.ui.dock;

import java.util.Arrays;
import java.util.function.Function;

import imgui.internal.ImGui;
import imgui.internal.flag.ImGuiDockNodeFlags;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.lauriichan.laylib.logger.ISimpleLogger;
import me.lauriichan.snowframe.ImGUIModule;
import me.lauriichan.snowframe.SignalModule;
import me.lauriichan.snowframe.SnowFrame;

public final class ImGuiDock {

    public static DockNode newDock() {
        return new DockNode();
    }

    static final class ImGuiDockNode {

        private final String dockId;

        private final float ratio;
        private final int splitDirection;
        private final ImGuiDockNode first, second;

        private final ImInt id = new ImInt();

        public ImGuiDockNode(final String dockId, final float ratio, final int splitDirection, final ImGuiDockNode first,
            final ImGuiDockNode second) {
            this.dockId = dockId;
            this.ratio = ratio;
            this.splitDirection = splitDirection;
            if (first == null && second != first) {
                throw new IllegalArgumentException("Either both nodes need to be defined or none");
            }
            this.first = first;
            this.second = second;
        }

    }

    private static final int DOCK_NODE_FLAGS = ImGuiDockNodeFlags.NoResize | ImGuiDockNodeFlags.NoCloseButton;
    private static final int DOCK_NODE_HIDDEN_FLAGS = DOCK_NODE_FLAGS | ImGuiDockNodeFlags.NoTabBar;

    private final SignalModule signalModule;
    private final ImGUIModule guiModule;
    private final ISimpleLogger logger;

    private final Object2ObjectMap<Class<?>, DockUIExtension> extensions;
    private final DockUIExtension[] extensionArray;
    private volatile ImGuiDockNode root;

    ImGuiDock(SnowFrame<?> frame, String dockId, ImGuiDockNode root) {
        this.signalModule = frame.module(SignalModule.class);
        this.guiModule = frame.module(ImGUIModule.class);
        this.logger = frame.logger();
        this.root = root;

        Object2ObjectOpenHashMap<Class<?>, DockUIExtension> extensions = new Object2ObjectOpenHashMap<>();
        frame.extension(DockUIExtension.class, true).callInstances(ext -> {
            if (!ext.dockId().equals(dockId)) {
                return;
            }
            extensions.put(ext.getClass(), ext);
        });
        extensions.trim();
        this.extensions = Object2ObjectMaps.unmodifiable(extensions);
        this.extensionArray = extensions.values().toArray(DockUIExtension[]::new);
        Arrays.sort(extensionArray, (d1, d2) -> {
            int cmp = Integer.compare(d2.sortPriority(), d1.sortPriority());
            if (cmp != 0) {
                return cmp;
            }
            return d1.title().compareTo(d2.title());
        });
    }

    public <E extends DockUIExtension> E get(Class<E> type) {
        DockUIExtension ext = extensions.get(type);
        if (ext == null) {
            for (Class<?> keyType : extensions.keySet()) {
                if (type.isAssignableFrom(keyType)) {
                    return type.cast(extensions.get(keyType));
                }
            }
            return null;
        }
        return type.cast(ext);
    }

    public DockUIExtension visibleDockOn(String targetId) {
        for (DockUIExtension extension : extensionArray) {
            if (!extension.isVisible() || !extension.targetId().equals(targetId)) {
                continue;
            }
            return extension;
        }
        return null;
    }

    public void render() {
        if (root != null) {
            setup();
            for (DockUIExtension extension : extensionArray) {
                extension.onDockSetup();
            }
        }
        ImGui.dockSpaceOverViewport();
        for (DockUIExtension extension : extensionArray) {
            extension.render(this, guiModule.windowPointer());
        }
    }

    private void setup() {
        signalModule.signalManager().call(DockSignal.PreDock.INST);
        ImGuiDockNode node = root;
        root = null;

        ImGuiViewport view = ImGui.getMainViewport();
        int dockId = ImGui.dockSpaceOverViewport();
        ImGui.dockBuilderRemoveNode(dockId);
        ImGui.dockBuilderAddNode(dockId);
        ImVec2 size = view.getSize();
        ImGui.dockBuilderSetNodeSize(dockId, size.x, size.y);
        node.id.set(dockId);

        Object2ObjectLinkedOpenHashMap<String, ObjectArrayList<String>> map = new Object2ObjectLinkedOpenHashMap<>();
        Function<String, ObjectArrayList<String>> builder = (_) -> new ObjectArrayList<String>();

        for (DockUIExtension extension : extensionArray) {
            ObjectArrayList<String> list = map.computeIfAbsent(extension.targetId(), builder);
            if (list.contains(extension.title())) {
                throw new IllegalArgumentException("Duplicated title: " + extension.title());
            }
            list.add(extension.title());
            logger.debug("Adding '" + extension.title() + "' to '" + extension.dockId() + "'");
        }

        if (node.first != null) {
            ObjectArrayList<ImGuiDockNode> queue = new ObjectArrayList<>();
            ObjectArrayList<ImGuiDockNode> windowDockQueue = new ObjectArrayList<>();
            queue.add(node);
            while (!queue.isEmpty()) {
                ImGuiDockNode next = queue.pop();
                if (next.first == null || next.second == null) {
                    continue;
                }
                logger.debug("Splitting '" + next.dockId + "': " + next.ratio);
                ImGui.dockBuilderSplitNode(next.id.get(), next.splitDirection, next.ratio, next.first.id, next.second.id);
                if (next.first.first != null) {
                    queue.push(next.first);
                } else {
                    windowDockQueue.push(next.first);
                }
                if (next.second.first != null) {
                    queue.push(next.second);
                } else {
                    windowDockQueue.push(next.second);
                }
            }
            ImGuiDockNode dock;
            while (!windowDockQueue.isEmpty()) {
                dock = windowDockQueue.pop();
                logger.debug("Docking: " + dock.dockId);
                dock(logger, dock.id.get(), map.get(dock.dockId));
            }
        } else {
            dock(logger, dockId, map.get("root"));
        }

        ImGui.dockBuilderFinish(dockId);
        signalModule.signalManager().call(DockSignal.PostDock.INST);
    }

    private void dock(ISimpleLogger logger, int id, ObjectArrayList<String> list) {
        ImGui.dockBuilderGetNode(id).setLocalFlags(list == null || list.size() <= 1 ? DOCK_NODE_HIDDEN_FLAGS : DOCK_NODE_FLAGS);
        if (list != null) {
            for (String window : list) {
                logger.debug("Docking '" + window + "' to '" + id + "'");
                ImGui.dockBuilderDockWindow(window, id);
            }
        }
    }

}

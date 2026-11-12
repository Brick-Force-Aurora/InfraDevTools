package de.brickforceaurora.devtools.ui.instruction;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.brickforceaurora.devtools.Constant;
import de.brickforceaurora.devtools.util.NFDUtil;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.resource.source.IDataSource;
import me.lauriichan.snowframe.resource.source.PathDataSource;

@Extension
public class CopyInstructionEditor extends InstructionEditor {

    private static final String CMD_FORMAT = "copy \"%s\" \"%s\"";

    private static final NFDUtil.Filter[] EMPTY_FILTER = new NFDUtil.Filter[0];
    private static final ImVec2 NO_PADDING = new ImVec2(0, 0);

    private static record Copy(String path, boolean dir) {}

    private final Object2ObjectLinkedOpenHashMap<String, Copy> map = new Object2ObjectLinkedOpenHashMap<>();
    private final ImString str = new ImString(Constant.FILE_PATH_LENGTH);

    public CopyInstructionEditor() {
        super("Copies", 0, true, true, false);
    }

    @Override
    public void reset() {
        map.clear();
    }
    
    public void legacyImport(String fromPath, String toPath) {
        if (map.containsKey(fromPath)) {
            return;
        }
        map.put(fromPath, new Copy(toPath, false));
    }

    @Override
    protected void onAdd(Path absolutePath, Path relativePath, boolean directory) {
        String path = absolutePath.toString();
        if (map.containsKey(path)) {
            return;
        }
        map.put(path, new Copy(null, directory));
    }

    @Override
    protected void renderContent() {
        ObjectBidirectionalIterator<Entry<String, Copy>> iterator = map.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, Copy> entry = iterator.next();
            ImGui.pushID("c-" + entry.getKey());
            try {
                if (ImGui.button("-")) {
                    iterator.remove();
                    continue;
                }
            } finally {
                ImGui.popID();
            }
            String targetPath = entry.getValue().path();
            ImGui.sameLine();
            ImGui.pushID("cs+" + entry.getKey());
            try {
                if (ImGui.button("Set")) {
                    if (entry.getValue().dir()) {
                        targetPath = NFDUtil.pickDirectory(gameDir());
                    } else {
                        String[] ext = entry.getKey().split("\\.");
                        NFDUtil.Filter[] filters = EMPTY_FILTER;
                        if (ext.length > 1) {
                            filters = new NFDUtil.Filter[] {
                                new NFDUtil.Filter(ext[ext.length - 1], ext[ext.length - 1])
                            };
                        }
                        targetPath = NFDUtil.pickFile(gameDir(), filters);
                    }
                    Path newPath = relativizePath(Paths.get(targetPath));
                    if (newPath == null) {
                        continue;
                    }
                    map.put(entry.getKey(), new Copy(newPath.toString(), entry.getValue().dir()));
                }
            } finally {
                ImGui.popID();
            }
            if (targetPath == null) {
                targetPath = "";
            }
            ImGui.sameLine();
            ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, NO_PADDING);
            ImGui.beginTable("##Table#" + entry.getKey(), 3, ImGuiTableFlags.SizingStretchProp);
            ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel, 0.5f);
            ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel, 0.025f);
            ImGui.tableSetupColumn("3", ImGuiTableColumnFlags.NoHeaderLabel, 0.5f);
            ImGui.tableNextColumn();
            str.set(entry.getKey());
            ImGui.pushItemWidth(-1f);
            ImGui.inputText("##SrcC#" + entry.getKey(), str, ImGuiInputTextFlags.ReadOnly);
            ImGui.popItemWidth();
            ImGui.tableNextColumn();
            ImGui.text(" => ");
            ImGui.tableNextColumn();
            str.set(targetPath);
            ImGui.pushItemWidth(-1f);
            if (ImGui.inputText("##TargetC#" + entry.getKey(), str)) {
                if (!str.get().equals(targetPath)) {
                    map.put(entry.getKey(), new Copy(str.get(), entry.getValue().dir()));
                }
            }
            ImGui.popItemWidth();
            ImGui.endTable();
            ImGui.popStyleVar();
        }
    }

    @Override
    public boolean isExportable() {
        return map.object2ObjectEntrySet().stream().allMatch(entry -> entry.getValue().path() != null);
    }

    @Override
    public void writeUpdate(IDataSource tmpDir, ObjectArrayList<String> instructions) throws IOException {
        for (Entry<String, Copy> entry : map.object2ObjectEntrySet()) {
            PathDataSource pathSrc = new PathDataSource(Paths.get(entry.getKey()));
            String targetName = ensureDeduplicatedFileName(tmpDir, pathSrc.name());
            pathSrc.transferTo(tmpDir.resolve(targetName));
            instructions.add(CMD_FORMAT.formatted(formatPath(targetName), formatPath(entry.getValue().path())));
        }
    }

    private String ensureDeduplicatedFileName(IDataSource tmpDir, String fileName) {
        IDataSource src = tmpDir.resolve(fileName);
        if (!src.exists()) {
            return fileName;
        }
        String modFileName = '0' + fileName;
        src = tmpDir.resolve(modFileName);
        for (int deduplication = 1; src.exists(); deduplication++) {
            src = tmpDir.resolve(modFileName = deduplication + fileName);
        }
        return modFileName;
    }

}

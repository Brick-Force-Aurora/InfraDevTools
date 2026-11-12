package de.brickforceaurora.devtools.ui.view;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.brickforceaurora.devtools.Constant;
import de.brickforceaurora.devtools.DevApp;
import de.brickforceaurora.devtools.FontAtlas;
import de.brickforceaurora.devtools.config.UpdateSettings;
import de.brickforceaurora.devtools.ui.dock.DockUIExtension;
import de.brickforceaurora.devtools.ui.dock.ImGuiDock;
import de.brickforceaurora.devtools.ui.instruction.InstructionEditor;
import de.brickforceaurora.devtools.ui.view.SidebarView.ISidebarView;
import de.brickforceaurora.devtools.util.NFDUtil;
import de.brickforceaurora.devtools.util.ParseUtil;
import de.brickforceaurora.devtools.util.github.GithubIntegration;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.lauriichan.laylib.json.IJson;
import me.lauriichan.laylib.json.JsonObject;
import me.lauriichan.laylib.logger.ISimpleLogger;
import me.lauriichan.snowframe.ConfigModule;
import me.lauriichan.snowframe.SnowFrame;
import me.lauriichan.snowframe.config.handler.JsonConfigHandler;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.extension.IExtensionPool;
import me.lauriichan.snowframe.resource.source.IDataSource;
import me.lauriichan.snowframe.util.Version;
import me.lauriichan.snowframe.util.http.HttpMethod;
import me.lauriichan.snowframe.util.http.HttpRequest;
import me.lauriichan.snowframe.util.http.HttpResponse;
import me.lauriichan.snowframe.util.http.data.MultiFormData;
import me.lauriichan.snowframe.util.http.type.HttpContentType;
import me.lauriichan.snowframe.util.instance.Shared;

@Shared
@Extension
public class PublishingView extends DockUIExtension implements ISidebarView {

    public static record ProductInfo(UUID id, String idStr, String name) {

        public ProductInfo(UUID id, String name) {
            this(id, id.toString(), name);
        }

    }

    private static enum ErrorType {

        BRICKFORCE_EXE_NOT_FOUND;

    }

    private static final String[] EMPTY_PRODUCT = new String[0];

    public final ImString gameFolderPath = new ImString(Constant.FILE_PATH_LENGTH);
    public final ImString apiKey = new ImString(Constant.FILE_PATH_LENGTH);
    public final ImString apiHost = new ImString(Constant.FILE_PATH_LENGTH);
    public final ImString productId = new ImString(36);

    public final ImString versionString = new ImString(15);
    public final ImBoolean experimentalRelease = new ImBoolean(false), overwrite = new ImBoolean(false);
    public final ImInt versionMajor = new ImInt(0), versionMinor = new ImInt(0), versionPatch = new ImInt(0),
        versionRevision = new ImInt(0);

    private final ImString githubRepository = new ImString(64);
    private final ImString githubToken = new ImString(256);
    private volatile String[] githubReleaseVersions = EMPTY_PRODUCT;
    private volatile ObjectList<GithubIntegration.GithubRelease> githubReleases;

    public final ImInt selectedProduct = new ImInt(0), selectedRelease = new ImInt(0);;

    private final ISimpleLogger logger;

    private final IExtensionPool<InstructionEditor> editors;
    private final UpdateSettings settings;

    private final IDataSource updateWorkDir, importWorkDir;
    private final AtomicBoolean exportInProgress = new AtomicBoolean(false), requestInProgress = new AtomicBoolean(false);

    public final ObjectArrayList<ProductInfo> products = new ObjectArrayList<>();
    private volatile String[] productNames = EMPTY_PRODUCT;

    public volatile ProductInfo product;

    private volatile Version target = new Version(0, 0, 0, -1);
    private volatile String versionTxt;

    private volatile ErrorType error = null, current = null;

    private volatile boolean githubImport = false;
    public volatile boolean importedFromGithub = false;

    public PublishingView(SnowFrame<DevApp> frame) {
        super("Publish", "main", "main");
        this.logger = frame.logger();
        this.editors = frame.extension(InstructionEditor.class, true);
        this.updateWorkDir = frame.resource("tmp://update");
        this.importWorkDir = frame.resource("tmp://import");
        versionString.inputData.allowedChars = "0123456789.-R";

        githubRepository.set("Brick-Force-Aurora/Brick-Force");

        settings = frame.module(ConfigModule.class).manager().config(UpdateSettings.class);
        setDefaults();
    }

    public void setDefaults() {
        try {
            target = Version.parse(settings.gameVersion() == null ? "0.0.0" : settings.gameVersion());
        } catch (RuntimeException _) {
        }
        versionTxt = target.toString();
        versionString.set(versionTxt);
        versionMajor.set(Math.max(target.major, 0));
        versionMinor.set(Math.max(target.minor, 0));
        versionPatch.set(Math.max(target.patch, 0));
        versionRevision.set(Math.max(target.revision, 0));

        if (settings.gameDirectory() != null && !settings.gameDirectory().isBlank()) {
            Path path = Paths.get(settings.gameDirectory());
            if (Files.exists(path)) {
                gameFolderPath.set(settings.gameDirectory());
            }
        }
        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
            apiKey.set(settings.apiKey());
        }
        if (settings.apiHost() != null && !settings.apiHost().isBlank()) {
            apiHost.set(settings.apiHost());
        }
    }

    public void reset() {
        if (importWorkDir.exists()) {
            try {
                importWorkDir.delete();
            } catch (IOException _) {
            }
        }
        setDefaults();
        resetEditors();
    }

    public void resetEditors() {
        editors.callInstances(editor -> editor.reset());
    }

    @Override
    protected void renderContent(ImGuiDock dock, long windowHandle) {
        ImGui.beginChild("Instructions", ImGuiWindowFlags.AlwaysVerticalScrollbar);
        editors.callInstances(editor -> editor.render(gameFolderPath.get(), gameFolderPath.isEmpty()));
        ImGui.endChild();
        ImGui.setItemDefaultFocus();

        doErrorPopup();
        doGithubPopup();
    }

    private String api(String path) {
        String host = apiHost.get();
        if (host.charAt(host.length() - 1) != '/') {
            host += '/';
        }
        return host + path;
    }

    private void doErrorPopup() {
        if (error != null) {
            ImGui.openPopup("Error");
            current = error;
            error = null;
        }
        if (current == null) {
            return;
        }
        if (ImGui.beginPopupModal("Error", ImGuiWindowFlags.NoResize)) {
            ImGui.setWindowSize(280f, 160f);
            switch (current) {
            case BRICKFORCE_EXE_NOT_FOUND:
                ImGui.textWrapped("Couldn't find 'BrickForce.exe' in selected game folder.");
                break;
            }
            ImGui.setCursorPosY(ImGui.getWindowSizeY() - ImGui.getTextLineHeightWithSpacing() * 1.75f);
            if (ImGui.button("Close", -1f, ImGui.getTextLineHeightWithSpacing() * 1.25f)) {
                ImGui.closeCurrentPopup();
                current = null;
            }
            ImGui.endPopup();
        }
    }

    private void doGithubPopup() {
        if (githubImport) {
            githubImport = false;
            ImGui.openPopup("GithubImport");
        }
        if (ImGui.beginPopupModal("GithubImport", ImGuiWindowFlags.NoResize)) {
            ImGui.setWindowSize(280f, 240f);
            ImGui.text("Github Repository");
            ImGui.pushItemWidth(-1f);
            ImGui.inputText("##GithubImport#Repository", githubRepository);
            float buttonSize = ImGui.getItemRectSizeY();
            ImGui.popItemWidth();
            ImGui.text("Github Auth Token");
            ImGui.pushItemWidth(-1f);
            ImGui.inputText("##GithubImport#AuthToken", githubToken);
            ImGui.popItemWidth();
            ImGui.beginTable("##GithubImport#Table", 2);
            ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.7f);
            ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.3f);
            ImGui.tableNextColumn();
            ImGui.text("Github Release");
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1f);
            ImGui.combo("##GithubImport#Combo", selectedRelease, githubReleaseVersions);
            ImGui.popItemWidth();
            ImGui.tableNextColumn();
            if (ImGui.button("Reload", -1f, buttonSize)) {
                try {
                    githubReleases = GithubIntegration.listReleases(githubRepository.get(), githubToken.get());
                    githubReleaseVersions = githubReleases.stream()
                        .map(release -> release.version().toString() + (release.preRelease() ? " (Experimental)" : ""))
                        .toArray(String[]::new);
                } catch (RuntimeException | IOException exp) {
                    logger.error("Failed to release github releases", exp);
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endTable();
            ImGui.setCursorPosY(ImGui.getWindowSizeY() - buttonSize * 1.5f);
            ImGui.beginTable("##GithubImport#Table2", 2);
            ImGui.tableNextColumn();
            ImGui.beginDisabled(selectedRelease.get() < 0 && selectedRelease.get() >= githubReleaseVersions.length);
            if (ImGui.button("Import", -1f, buttonSize)) {
                try {
                    GithubIntegration.importRelease(this, importWorkDir, githubReleases.get(selectedRelease.get()), githubToken.get());
                } catch (RuntimeException | IOException exp) {
                    logger.error("Failed to import github release", exp);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.endDisabled();
            ImGui.tableNextColumn();
            if (ImGui.button("Close", -1f, buttonSize)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endTable();
            ImGui.endPopup();
        }
    }

    @Override
    public void renderSidebar(SidebarView view, ImGuiDock dock, long windowHandle) {
        ImGui.pushFont(FontAtlas.NOTO_SANS_BOLD, 24f);
        ImGui.text("Update Settings");
        ImGui.popFont();

        ImGui.beginDisabled(importedFromGithub);
        ImGui.beginGroup();
        ImGui.beginTable("##Publish#GameDirectory#Table", 2);
        ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.7f);
        ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.3f);
        ImGui.tableNextColumn();
        ImGui.text("Game directory");
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1f);
        ImGui.inputText("##Publish#GameDirectory", gameFolderPath, ImGuiInputTextFlags.ReadOnly);
        float buttonSize = ImGui.getItemRectSizeY();
        ImGui.popItemWidth();
        ImGui.tableNextColumn();
        if (ImGui.button("Browse", -1f, buttonSize)) {
            String strPath = NFDUtil.pickDirectory(gameFolderPath.get());
            if (strPath != null) {
                Path path = Paths.get(strPath).resolve("BrickForce.exe");
                if (!Files.exists(path)) {
                    error = ErrorType.BRICKFORCE_EXE_NOT_FOUND;
                } else {
                    gameFolderPath.set(strPath);
                    settings.gameDirectory(strPath);
                }
            }
        }
        ImGui.setItemDefaultFocus();
        ImGui.endTable();
        ImGui.endGroup();

        ImGui.dummy(0f, 12f);

        ImGui.beginGroup();
        ImGui.beginTable("##Publish#GameDirectory#Table", 2);
        ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.7f);
        ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.3f);
        ImGui.tableNextColumn();
        ImGui.text("Product");
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1f);
        if (ImGui.combo("##Publish#Product", selectedProduct, productNames)) {
            updateProduct();
        }
        ImGui.popItemWidth();
        ImGui.tableNextColumn();
        ImGui.beginDisabled(requestInProgress.get());
        if (ImGui.button("Request", -1f, buttonSize)) {
            requestInProgress.set(true);
            DevApp.SCHEDULER.submit(() -> {
                products.clear();
                try {
                    for (IJson<?> productEntry : new HttpRequest().url(api("product/list")).call(HttpContentType.JSON).data().value()
                        .asJsonObject().getAsObject("data").getAsArray("products")) {
                        JsonObject productObj = productEntry.asJsonObject();
                        products
                            .add(new ProductInfo(ParseUtil.uuidFromString(productObj.getAsString("id")), productObj.getAsString("name")));
                    }
                    String[] names = new String[products.size()];
                    for (int i = 0; i < products.size(); i++) {
                        names[i] = products.get(i).name();
                    }
                    productNames = names;
                } catch (RuntimeException | IOException e) {
                    productNames = EMPTY_PRODUCT;
                    logger.error("Failed to request products", e);
                } finally {
                    requestInProgress.set(false);
                }
                if (selectedProduct.get() < 0) {
                    selectedProduct.set(0);
                } else if (selectedProduct.get() >= products.size()) {
                    selectedProduct.set(products.size() - 1);
                }
                updateProduct();
            });
        }
        ImGui.endDisabled();
        ImGui.setItemDefaultFocus();
        ImGui.endTable();
        ImGui.text("Product Id (Read only)");
        ImGui.pushItemWidth(-1f);
        ImGui.inputText("##Publish#ProductId", productId, ImGuiInputTextFlags.ReadOnly);
        ImGui.popItemWidth();
        ImGui.endGroup();

        ImGui.dummy(0f, 12f);

        ImGui.beginGroup();
        ImGui.text("Version: " + versionTxt);
        ImGui.pushItemWidth(-1f);
        if (ImGui.inputText("##Publish#Version#Full", versionString)) {
            try {
                target = Version.parse(versionString.get());
                versionMajor.set(Math.max(target.major, 0));
                versionMinor.set(Math.max(target.minor, 0));
                versionPatch.set(Math.max(target.patch, 0));
                versionRevision.set(Math.max(target.revision, 0));
                experimentalRelease.set(target.revision > -1);
                versionTxt = target.toString();
                settings.gameVersion(versionTxt);
                versionString.set(versionTxt);
            } catch (IllegalArgumentException iae) {
                // This means invalid
                target = null;
                versionTxt = "Invalid";
            }
        }
        ImGui.popItemWidth();

        ImGui.dummy(0f, 12f);

        if (ImGui.beginTable("##Publish#Version", 2)) {
            ImGui.tableNextColumn();
            ImGui.text("Major");
            ImGui.tableNextColumn();
            ImGui.text("Minor");
            ImGui.tableNextRow();
            intField("##Publish#Version#Major", versionMajor, false);
            intField("##Publish#Version#Minor", versionMinor, false);
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.text("Patch");
            ImGui.tableNextColumn();
            ImGui.text("Revision");
            intField("##Publish#Version#Patch", versionPatch, false);
            intField("##Publish#Version#Revision", versionRevision, !experimentalRelease.get());
            ImGui.endTable();
        }
        ImGui.dummy(0f, 8f);
        ImGui.pushItemWidth(-1f);
        if (ImGui.checkbox("Experimental", experimentalRelease)) {
            if (experimentalRelease.get()) {
                if (versionRevision.get() < 0) {
                    versionRevision.set(0);
                }
            }
            updateFullVersion();
        }
        ImGui.popItemWidth();
        ImGui.endGroup();
        ImGui.endDisabled();

        ImGui.dummy(0f, ImGui.getContentRegionAvailY() - 184f);

        ImGui.beginGroup();
        ImGui.text("Api host");
        ImGui.pushItemWidth(-1f);
        if (ImGui.inputText("##Publish#ApiHost", apiHost)) {
            settings.apiHost(apiHost.get());
        }
        ImGui.popItemWidth();
        ImGui.text("Api key");
        ImGui.pushItemWidth(-1f);
        if (ImGui.inputText("##Publish#ApiKey", apiKey)) {
            settings.apiKey(apiKey.get());
        }
        ImGui.popItemWidth();
        ImGui.endGroup();

        ImGui.dummy(0f, 12f);

        ImGui.beginGroup();
        boolean exportable = true;
        for (InstructionEditor editor : editors.extensions()) {
            if (!editor.isExportable()) {
                exportable = false;
                break;
            }
        }
        ImGui.beginDisabled(exportInProgress.get());
        if (ImGui.beginTable("##Publish#OverwriteImport", 2)) {
            ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.595f);
            ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthStretch, 0.405f);
            ImGui.tableNextColumn();
            if (importedFromGithub) {
                if (ImGui.button("Cancel import", -1f, buttonSize)) {
                    importedFromGithub = false;
                    reset();
                }
            } else {
                if (ImGui.button("Import from Github", -1f, buttonSize)) {
                    githubImport = true;
                }
            }
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1f);
            ImGui.checkbox("Overwrite", overwrite);
            ImGui.popItemWidth();
            ImGui.endTable();
        }
        ImGui.endDisabled();
        ImGui.beginDisabled(target == null || product == null || apiKey.isEmpty() || apiKey.get().isBlank() || apiHost.isEmpty()
            || apiHost.get().isBlank() || !exportable || exportInProgress.get());
        if (ImGui.button("Publish", -1f, buttonSize)) {
            exportInProgress.set(true);
            Version version = target;
            String apiKey = this.apiKey.get();
            logger.info("[PUBLISH] Enqueued publishing task for version {0} to host '{1}'", version, apiHost);
            boolean allowsOverwrite = overwrite.get();
            DevApp.SCHEDULER.submit(() -> {
                try {
                    logger.info("[PUBLISH] Cleaning working directory '{0}'", updateWorkDir.getPath());
                    updateWorkDir.delete();
                    updateWorkDir.createAsContainer();
                    ObjectArrayList<String> instructions = new ObjectArrayList<>();
                    logger.info("[PUBLISH] Sorting editors");
                    InstructionEditor[] editors = this.editors.extensions().toArray(InstructionEditor[]::new);
                    Arrays.sort(editors, (e1, e2) -> Integer.compare(e1.instructionsPriority(), e2.instructionsPriority()));
                    logger.info("[PUBLISH] Writing update to working directory");
                    for (InstructionEditor editor : editors) {
                        editor.writeUpdate(updateWorkDir, instructions);
                    }
                    logger.info("[PUBLISH] Update written, generated {0} instruction(s).", instructions.size());
                    logger.info("[PUBLISH] Zipping working directory");
                    FastByteArrayOutputStream byteOut = new FastByteArrayOutputStream();
                    try (ZipOutputStream zip = new ZipOutputStream(byteOut, StandardCharsets.UTF_8)) {
                        zip.setComment("BrickForce Aurora Update (%s)".formatted(version));
                        zip.setLevel(7);
                        for (IDataSource src : updateWorkDir.getContents()) {
                            addToZip("", src, zip);
                        }
                    }
                    String combinedInstructions = String.join("\n", instructions);
                    if (logger.isDebug()) {
                        logger.debug("[PUBLISH] Cleaning working dir again to write debug items");
                        updateWorkDir.delete();
                        logger.debug("[PUBLISH] Writing update zip to disk");
                        try (OutputStream output = updateWorkDir.resolve("update.zip").openWritableStream()) {
                            output.write(byteOut.array, 0, byteOut.length);
                        }
                        logger.debug("[PUBLISH] Writing instructions to disk");
                        try (BufferedWriter writer = updateWorkDir.resolve("update.instr").openWriter()) {
                            writer.write(combinedInstructions);
                        }
                    }
                    logger.info("[PUBLISH] Building HTTP Request");
                    MultiFormData data = new MultiFormData();
                    JsonObject info = new JsonObject();
                    info.put("version", IJson.of(version.toString()));
                    info.put("overwrite", IJson.of(allowsOverwrite));
                    data.put("info", HttpContentType.JSON, info);
                    data.put("instructions", "update.instr", HttpContentType.TEXT.disguise("bfaurora/update-instructions"),
                        combinedInstructions);
                    data.put("update_zip", "update.zip", HttpContentType.BINARY.disguise("application/zip"), byteOut.toByteArray());
                    logger.info("[PUBLISH] Posting HTTP Request");
                    HttpResponse<IJson<?>> response = new HttpRequest().method(HttpMethod.POST).url(api("update/publish"))
                        .param("product_id", product.idStr().replace("-", "")).header("X-API-KEY", apiKey)
                        .call(HttpContentType.JSON, HttpContentType.multiFormData(), data);
                    logger.info("Response HTTP Code: {0} ({1})", response.code().code(), response.code().name());
                    logger.info(JsonConfigHandler.WRITER.toString(response.data().value()));
                } catch (RuntimeException | IOException e) {
                    logger.error("Failed to publish update {0}", e, version);
                } finally {
                    exportInProgress.set(false);
                    if (importedFromGithub) {
                        importedFromGithub = false;
                        reset();
                    } else {
                        resetEditors();
                    }
                }
            });
        }
        ImGui.endDisabled();
        ImGui.endGroup();
    }

    private void addToZip(String path, IDataSource src, ZipOutputStream zip) throws IOException {
        if (src.isContainer()) {
            path = path + src.name() + '/';
            zip.putNextEntry(new ZipEntry(path));
            zip.closeEntry();
            for (IDataSource childSrc : src.getContents()) {
                addToZip(path, childSrc, zip);
            }
            return;
        }
        ZipEntry entry = new ZipEntry(path + src.name());
        entry.setSize(src.size());
        zip.putNextEntry(entry);
        src.transferTo(zip);
        zip.closeEntry();
    }

    private void intField(String id, ImInt version, boolean readonly) {
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1f);
        if (ImGui.inputInt(id, version, 1, 2, readonly ? ImGuiInputTextFlags.ReadOnly : 0)) {
            if (version.get() < 0) {
                version.set(0);
            }
            updateFullVersion();
        }
        ImGui.popItemWidth();
    }

    private void updateProduct() {
        if (selectedProduct.get() < products.size() && selectedProduct.get() >= 0) {
            product = products.get(selectedProduct.get());
            productId.set(product.idStr());
        } else {
            product = null;
            productId.set("");
        }
    }

    public void updateFullVersion() {
        Version version = experimentalRelease.get()
            ? new Version(versionMajor.get(), versionMinor.get(), versionPatch.get(), versionRevision.get())
            : new Version(versionMajor.get(), versionMinor.get(), versionPatch.get());
        versionString.set(version.toString());
        versionTxt = versionString.get();
        target = version;
        if (!importedFromGithub) {
            settings.gameVersion(versionTxt);
        }
    }

    public <E extends InstructionEditor> E editor(Class<E> type) {
        for (InstructionEditor editor : editors.extensions()) {
            if (type.isInstance(editor)) {
                return type.cast(editor);
            }
        }
        return null;
    }

}

package de.brickforceaurora.devtools.util.github;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.brickforceaurora.devtools.DevApp;
import de.brickforceaurora.devtools.ui.instruction.CopyInstructionEditor;
import de.brickforceaurora.devtools.ui.instruction.DeleteInstructionEditor;
import de.brickforceaurora.devtools.ui.view.PublishingView;
import de.brickforceaurora.devtools.ui.view.PublishingView.ProductInfo;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.laylib.json.IJson;
import me.lauriichan.laylib.json.JsonArray;
import me.lauriichan.laylib.json.JsonObject;
import me.lauriichan.snowframe.resource.source.IDataSource;
import me.lauriichan.snowframe.util.Version;
import me.lauriichan.snowframe.util.http.HttpCode;
import me.lauriichan.snowframe.util.http.HttpRequest;
import me.lauriichan.snowframe.util.http.HttpResponse;
import me.lauriichan.snowframe.util.http.type.HttpContentType;

public final class GithubIntegration {

    private static final String GITHUB_RELEASE = "https://api.github.com/repos/%s/releases/tags/%s";
    private static final String GITHUB_TAGS = "https://api.github.com/repos/%s/tags";

    private static final int ITEMS_PER_PAGE_COUNT = 40;

    private GithubIntegration() {
        throw new UnsupportedOperationException();
    }

    public static record GithubRelease(Version version, String zipUrl, boolean preRelease) {}

    public static ObjectList<GithubRelease> listReleases(String repository, String authToken) throws IOException {
        final String releaseUrl = GITHUB_RELEASE.formatted(repository, "%s");
        final String tagsUrl = GITHUB_TAGS.formatted(repository);

        final GithubAuthenticator auth = new GithubAuthenticator(authToken);
        final HttpRequest request = new HttpRequest().url(tagsUrl).authenticator(auth);
        final HttpRequest assetRequest = new HttpRequest().authenticator(auth);

        ObjectArrayList<GithubRelease> releases = new ObjectArrayList<>();

        int page = 1;
        while (true) {
            final HttpResponse<IJson<?>> response = callGithub(
                request.clearParameters().readTimeout(5000).param("per_page", ITEMS_PER_PAGE_COUNT).param("page", page++),
                HttpContentType.JSON);
            if (response == null || response.code() == HttpCode.NOT_FOUND) {
                break;
            }
            final JsonArray array = response.data().value().asJsonArray();
            if (array.isEmpty()) {
                break;
            }
            for (final IJson<?> arrayValue : array) {
                if (!arrayValue.isObject()) {
                    continue;
                }
                final JsonObject object = arrayValue.asJsonObject();
                final String tagName = object.getAsString("name");
                if (tagName == null) {
                    continue;
                }
                final Version version = Version.parse(tagName);
                final HttpResponse<IJson<?>> assetResponse = callGithub(assetRequest.url(releaseUrl.formatted(tagName)),
                    HttpContentType.JSON);
                if (assetResponse == null || assetResponse.code() == HttpCode.NOT_FOUND) {
                    continue;
                }
                final IJson<?> releaseValue = assetResponse.data().value();
                if (!releaseValue.isObject()) {
                    continue;
                }
                final JsonObject releaseObject = releaseValue.asJsonObject();
                if (releaseObject.getAsBoolean("draft")) {
                    continue;
                }
                for (final IJson<?> value : releaseObject.getAsArray("assets")) {
                    if (!value.isObject()) {
                        continue;
                    }
                    final JsonObject assetObject = value.asJsonObject();
                    final String assetName = assetObject.getAsString("name");
                    if (assetName != null && assetName.endsWith(".zip")) {
                        final String downloadUrl = assetObject.getAsString("browser_download_url");
                        if (downloadUrl == null || downloadUrl.isBlank()) {
                            continue;
                        }
                        releases.add(new GithubRelease(version, downloadUrl, releaseObject.getAsBoolean("prerelease", false)));
                        break;
                    }
                }
            }
            // We can stop early if the array has less than 40 entries
            if (array.size() < ITEMS_PER_PAGE_COUNT) {
                break;
            }
        }
        if (releases.isEmpty()) {
            return ObjectLists.emptyList();
        }
        Collections.reverse(releases);
        return ObjectLists.unmodifiable(releases);
    }

    static <T> HttpResponse<T> callGithub(final HttpRequest request, final HttpContentType<T> responseType) throws IOException {
        final HttpResponse<T> response = request.call(responseType);
        if (response.code() == HttpCode.FORBIDDEN || response.code() == HttpCode.TOO_MANY_REQUESTS) {
            throw new IOException("We hit a rate limit");
        }
        return response;
    }

    public static void importRelease(PublishingView view, IDataSource workDir, GithubRelease release, String authToken) throws IOException {
        ProductInfo product = view.products.stream().filter(info -> info.name().equals("aurora")).findFirst()
            .orElseThrow(() -> new IllegalStateException("Aurora product is not available"));
        final HttpResponse<byte[]> response = callGithub(
            new HttpRequest().url(release.zipUrl()).readTimeout(2500).authenticator(new GithubAuthenticator(authToken)),
            HttpContentType.BINARY);
        if (response == null || response.code() != HttpCode.OK) {
            throw new IOException("Couldn't download release");
        }
        workDir.delete();
        workDir.createAsContainer();
        DevApp.LOGGER.debug("Importing to '{0}'", workDir.getPath());
        try (ZipInputStream zip = new ZipInputStream(new FastByteArrayInputStream(response.data().value()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    workDir.resolve(entry.getName()).createAsContainer();
                    continue;
                }
                try (OutputStream outputStream = workDir.resolve(entry.getName()).openWritableStream()) {
                    zip.transferTo(outputStream);
                }
            }
        }
        IDataSource infoSrc = workDir.resolve("info");
        if (!infoSrc.isResource()) {
            throw new IOException("No update instructions found in release");
        }
        view.resetEditors();
        view.importedFromGithub = true;
        view.gameFolderPath.set(workDir.getPath());
        view.selectedProduct.set(view.products.indexOf(product));
        view.productId.set(product.idStr());
        view.product = product;
        DeleteInstructionEditor deleteEditor = view.editor(DeleteInstructionEditor.class);
        CopyInstructionEditor copyEditor = view.editor(CopyInstructionEditor.class);
        try (BufferedReader reader = infoSrc.openReader()) {
            String line;
            String[] parts;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("=")) {
                    continue;
                }
                parts = line.split("=");
                // Don't allow target to be outside of the target folder, generally we don't need '..'
                if ((parts.length > 2) || parts[1].contains("..")) {
                    continue;
                }
                if ("delete".equalsIgnoreCase(parts[0])) {
                    deleteEditor.legacyImport(parts[1]);
                    continue;
                }
                IDataSource src = workDir.resolve(parts[0]);
                if (src.isResource() && parts[1].endsWith("/")) {
                    parts[1] += src.name();
                }
                copyEditor.legacyImport(src.getPath(), parts[1]);
            }
        }
        view.versionMajor.set(Math.max(release.version().major, 0));
        view.versionMinor.set(Math.max(release.version().minor, 0));
        view.versionPatch.set(Math.max(release.version().patch, 0));
        view.versionRevision.set(Math.max(release.version().revision, 0));
        if (release.preRelease()) {
            if (view.versionRevision.get() < 0) {
                view.versionRevision.set(0);
            }
            view.experimentalRelease.set(true);
        } else {
            view.experimentalRelease.set(false);
        }
        view.updateFullVersion();
    }

}

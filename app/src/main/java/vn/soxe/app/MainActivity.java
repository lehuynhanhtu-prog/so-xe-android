package vn.soxe.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String DRIVE_FILE = "so-xe-data.json";
    private static final String ATTACHMENT_FOLDER = "Sổ xe";
    private static final String PREFS = "soxe_native";
    private static final String PREF_DISCONNECTED = "drive_disconnected";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String exportJson;
    private volatile String currentAccessToken;
    private String attachmentFolderId;
    private PendingSync pendingSync;

    private ActivityResultLauncher<IntentSenderRequest> authorizationLauncher;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    private static final class PendingSync {
        final String localJson;
        final boolean dirty;
        final boolean interactive;

        PendingSync(String localJson, boolean dirty, boolean interactive) {
            this.localJson = localJson;
            this.dirty = dirty;
            this.interactive = interactive;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerLaunchers();
        createWebView(savedInstanceState);
    }

    private void registerLaunchers() {
        authorizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                activityResult -> {
                    if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                        nativeError("Anh chưa cấp quyền Google Drive. Dữ liệu vẫn được giữ an toàn trên điện thoại.");
                        return;
                    }
                    try {
                        AuthorizationResult result = Identity.getAuthorizationClient(this)
                                .getAuthorizationResultFromIntent(activityResult.getData());
                        continueAuthorizedSync(result);
                    } catch (ApiException error) {
                        nativeError("Không thể hoàn tất kết nối Google Drive: " + friendlyAuthError(error));
                    }
                });

        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (fileChooserCallback == null) return;
                    Uri[] uris = WebChromeClient.FileChooserParams.parseResult(
                            result.getResultCode(), result.getData());
                    fileChooserCallback.onReceiveValue(uris);
                    fileChooserCallback = null;
                });

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null || exportJson == null) {
                        runJs("window.nativeExportResult(false)");
                        return;
                    }
                    String content = exportJson;
                    exportJson = null;
                    ioExecutor.execute(() -> {
                        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                            if (stream == null) throw new IOException("Không mở được tệp");
                            stream.write(content.getBytes(StandardCharsets.UTF_8));
                            runJs("window.nativeExportResult(true)");
                        } catch (Exception error) {
                            runJs("window.nativeExportResult(false)");
                        }
                    });
                });
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void createWebView(@Nullable Bundle savedInstanceState) {
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidDrive");
        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    @NonNull WebView view, @NonNull WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    @NonNull WebView view, @NonNull WebResourceRequest request) {
                return openExternalWhenNeeded(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternalWhenNeeded(Uri.parse(url));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    fileChooserLauncher.launch(params.createIntent());
                } catch (ActivityNotFoundException error) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "Không tìm thấy ứng dụng chọn tệp.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        setContentView(webView);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
    }

    private boolean openExternalWhenNeeded(Uri uri) {
        if (APP_HOST.equalsIgnoreCase(uri.getHost())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Không tìm thấy ứng dụng để mở liên kết.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidDrive");
            webView.destroy();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public void syncData(String localJson, boolean dirty, boolean interactive) {
            runOnUiThread(() -> requestDriveAuthorization(localJson, dirty, interactive));
        }

        @JavascriptInterface
        public void disconnect() {
            runOnUiThread(MainActivity.this::disconnectDrive);
        }

        @JavascriptInterface
        public void exportJson(String json) {
            runOnUiThread(() -> {
                exportJson = json;
                exportLauncher.launch("so-xe-data.json");
            });
        }

        @JavascriptInterface
        public boolean hasDriveToken() {
            return currentAccessToken != null && !currentAccessToken.isEmpty();
        }

        @JavascriptInterface
        public void uploadAttachment(String expenseId, String fileName, String mimeType,
                                     String base64Data, long fileSize) {
            String token = currentAccessToken;
            if (token == null || token.isEmpty()) {
                runJs("window.nativeAttachmentError(" + JSONObject.quote(
                        "Hãy kết nối Google Drive trước khi đính kèm tệp.") + ")");
                return;
            }
            ioExecutor.execute(() -> performAttachmentUpload(token, expenseId, fileName,
                    mimeType, base64Data, fileSize));
        }

        @JavascriptInterface
        public void deleteAttachment(String fileId, String requestId) {
            String token = currentAccessToken;
            if (token == null || token.isEmpty()) {
                runJs("window.nativeAttachmentDeleteError(" + JSONObject.quote(requestId) + ","
                        + JSONObject.quote("Hãy kết nối Google Drive trước khi xóa tệp.") + ")");
                return;
            }
            ioExecutor.execute(() -> {
                try {
                    http(token, "DELETE", "https://www.googleapis.com/drive/v3/files/"
                            + URLEncoder.encode(fileId, "UTF-8"), null, null);
                    runJs("window.nativeAttachmentDeleted(" + JSONObject.quote(requestId) + ")");
                } catch (Exception error) {
                    if (error instanceof HttpStatusException
                            && ((HttpStatusException) error).status == 404) {
                        runJs("window.nativeAttachmentDeleted(" + JSONObject.quote(requestId) + ")");
                        return;
                    }
                    if (error instanceof HttpStatusException
                            && ((HttpStatusException) error).status == 401) {
                        clearInvalidToken(token);
                    }
                    runJs("window.nativeAttachmentDeleteError(" + JSONObject.quote(requestId) + ","
                            + JSONObject.quote(error.getMessage() == null
                                    ? "Không xóa được tệp trên Google Drive" : error.getMessage()) + ")");
                }
            });
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Không tìm thấy ứng dụng mở tệp.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public String appVersion() {
            return "2.0.11";
        }
    }

    private void requestDriveAuthorization(String localJson, boolean dirty, boolean interactive) {
        if (!interactive && getPreferences().getBoolean(PREF_DISCONNECTED, false)) {
            nativeError("Đã lưu trên điện thoại · chưa kết nối Drive");
            return;
        }
        pendingSync = new PendingSync(localJson, dirty, interactive);
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Arrays.asList(new Scope(DRIVE_SCOPE), new Scope(DRIVE_FILE_SCOPE)))
                .build();
        Identity.getAuthorizationClient(this)
                .authorize(request)
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        if (!interactive) {
                            nativeError("Đã lưu trên điện thoại. Nhấn “Kết nối tài khoản Google” để bật đồng bộ Drive.");
                            return;
                        }
                        authorizationLauncher.launch(new IntentSenderRequest.Builder(
                                result.getPendingIntent().getIntentSender()).build());
                    } else {
                        continueAuthorizedSync(result);
                    }
                })
                .addOnFailureListener(error -> nativeError(
                        "Không thể kết nối Google Drive: " + friendlyAuthError(error)));
    }

    private void continueAuthorizedSync(AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || token.isEmpty() || pendingSync == null) {
            nativeError("Google không trả về quyền truy cập Drive. Hãy thử kết nối lại.");
            return;
        }
        currentAccessToken = token;
        attachmentFolderId = null;
        getPreferences().edit().putBoolean(PREF_DISCONNECTED, false).apply();
        PendingSync work = pendingSync;
        pendingSync = null;
        runJs("window.nativeDriveAuthorized()");
        ioExecutor.execute(() -> performDriveSync(token, work));
    }

    private void performDriveSync(String token, PendingSync work) {
        try {
            JSONObject local = new JSONObject(work.localJson);
            DriveFile remoteFile = findDriveFile(token);
            if (remoteFile == null) {
                createDriveFile(token, work.localJson);
                nativeSaved(local.optString("updatedAt"));
                return;
            }
            if (work.dirty) {
                updateDriveFile(token, remoteFile.id, work.localJson);
                nativeSaved(local.optString("updatedAt"));
                return;
            }
            String remoteJson = downloadDriveFile(token, remoteFile.id);
            JSONObject remote = new JSONObject(remoteJson);
            if (!remote.has("cars") || !remote.has("expenses")) {
                throw new IOException("Tệp dữ liệu trên Drive không đúng định dạng");
            }
            runJs("window.nativeDriveLoaded(" + JSONObject.quote(remoteJson) + ")");
        } catch (Exception error) {
            if (error instanceof HttpStatusException && ((HttpStatusException) error).status == 401) {
                clearInvalidToken(currentAccessToken);
            }
            nativeError(error.getMessage() == null ? "Đồng bộ Google Drive thất bại" : error.getMessage());
        }
    }

    private static final class DriveFile {
        final String id;
        DriveFile(String id) { this.id = id; }
    }

    private DriveFile findDriveFile(String token) throws IOException, JSONException {
        String query = URLEncoder.encode("name='" + DRIVE_FILE + "' and trashed=false", "UTF-8");
        String url = "https://www.googleapis.com/drive/v3/files?q=" + query
                + "&spaces=appDataFolder&fields=files(id,modifiedTime)&pageSize=1";
        JSONObject response = new JSONObject(http(token, "GET", url, null, null));
        JSONArray files = response.optJSONArray("files");
        return files == null || files.length() == 0
                ? null : new DriveFile(files.getJSONObject(0).getString("id"));
    }

    private String downloadDriveFile(String token, String fileId) throws IOException {
        return http(token, "GET", "https://www.googleapis.com/drive/v3/files/"
                + fileId + "?alt=media", null, null);
    }

    private void updateDriveFile(String token, String fileId, String json) throws IOException {
        http(token, "PATCH", "https://www.googleapis.com/upload/drive/v3/files/"
                + fileId + "?uploadType=media", "application/json; charset=UTF-8", json);
    }

    private void createDriveFile(String token, String json) throws IOException, JSONException {
        String boundary = "soxe-" + System.currentTimeMillis();
        JSONObject metadata = new JSONObject()
                .put("name", DRIVE_FILE)
                .put("mimeType", "application/json")
                .put("parents", new JSONArray().put("appDataFolder"));
        String body = "--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + metadata + "\r\n--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + json + "\r\n--" + boundary + "--";
        http(token, "POST",
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                "multipart/related; boundary=" + boundary, body);
    }

    private void performAttachmentUpload(String token, String expenseId, String fileName,
                                         String mimeType, String base64Data, long fileSize) {
        try {
            byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);
            if (fileBytes.length > 15 * 1024 * 1024) {
                throw new IOException("Tệp đính kèm lớn hơn giới hạn 15 MB.");
            }
            String folderId = findOrCreateAttachmentFolder(token);
            String boundary = "soxe-attachment-" + System.currentTimeMillis();
            JSONObject metadata = new JSONObject()
                    .put("name", fileName)
                    .put("parents", new JSONArray().put(folderId));
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                    + metadata + "\r\n--" + boundary + "\r\nContent-Type: "
                    + (mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType)
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
            String response = httpBytes(token, "POST",
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,webViewLink",
                    "multipart/related; boundary=" + boundary, body.toByteArray());
            JSONObject uploaded = new JSONObject(response);
            JSONObject attachment = new JSONObject()
                    .put("driveFileId", uploaded.getString("id"))
                    .put("name", uploaded.optString("name", fileName))
                    .put("mimeType", uploaded.optString("mimeType", mimeType))
                    .put("size", uploaded.optLong("size", fileSize))
                    .put("webViewLink", uploaded.optString("webViewLink"))
                    .put("uploadedAt", String.valueOf(System.currentTimeMillis()));
            runJs("window.nativeAttachmentUploaded(" + JSONObject.quote(expenseId) + ","
                    + JSONObject.quote(attachment.toString()) + ")");
        } catch (Exception error) {
            if (error instanceof HttpStatusException && ((HttpStatusException) error).status == 401) {
                clearInvalidToken(currentAccessToken);
            }
            runJs("window.nativeAttachmentError(" + JSONObject.quote(error.getMessage() == null
                    ? "Không tải được tệp đính kèm lên Google Drive" : error.getMessage()) + ")");
        }
    }

    private String findOrCreateAttachmentFolder(String token) throws IOException, JSONException {
        if (attachmentFolderId != null && !attachmentFolderId.isEmpty()) return attachmentFolderId;
        String query = URLEncoder.encode("name='" + ATTACHMENT_FOLDER
                + "' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8");
        JSONObject response = new JSONObject(http(token, "GET",
                "https://www.googleapis.com/drive/v3/files?q=" + query
                        + "&spaces=drive&fields=files(id,name)&pageSize=10", null, null));
        JSONArray files = response.optJSONArray("files");
        if (files != null && files.length() > 0) {
            attachmentFolderId = files.getJSONObject(0).getString("id");
            return attachmentFolderId;
        }
        JSONObject metadata = new JSONObject()
                .put("name", ATTACHMENT_FOLDER)
                .put("mimeType", "application/vnd.google-apps.folder");
        JSONObject created = new JSONObject(http(token, "POST",
                "https://www.googleapis.com/drive/v3/files?fields=id,name",
                "application/json; charset=UTF-8", metadata.toString()));
        attachmentFolderId = created.getString("id");
        return attachmentFolderId;
    }

    private String httpBytes(String token, String method, String address,
                             String contentType, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(input);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, driveErrorMessage(status, response));
        }
        return response;
    }

    private String http(String token, String method, String address,
                        @Nullable String contentType, @Nullable String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(input);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, driveErrorMessage(status, response));
        }
        return response;
    }

    private String readAll(@Nullable InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static final class HttpStatusException extends IOException {
        final int status;
        HttpStatusException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private String driveErrorMessage(int status, String response) {
        if (status == 401) return "Phiên Google đã hết hạn. Hãy nhấn Đồng bộ ngay để kết nối lại.";
        if (status == 403) return "Google Drive từ chối quyền truy cập. Hãy kiểm tra Drive API và OAuth Android.";
        if (status >= 500) return "Google Drive đang tạm thời gián đoạn. Dữ liệu vẫn được lưu trên điện thoại.";
        try {
            String message = new JSONObject(response).optJSONObject("error").optString("message");
            if (!message.isEmpty()) return "Lỗi Google Drive: " + message;
        } catch (Exception ignored) { }
        return "Không thể đồng bộ Google Drive (mã " + status + ").";
    }

    private void clearInvalidToken(@Nullable String token) {
        if (token == null || token.isEmpty()) return;
        Identity.getAuthorizationClient(this).clearToken(
                ClearTokenRequest.builder().setToken(token).build());
        currentAccessToken = null;
    }

    private void disconnectDrive() {
        getPreferences().edit().putBoolean(PREF_DISCONNECTED, true).apply();
        clearInvalidToken(currentAccessToken);
        attachmentFolderId = null;
        runJs("window.nativeDriveDisconnected()");
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String friendlyAuthError(Exception error) {
        String message = error.getMessage();
        if (message != null && message.contains("10")) {
            return "OAuth Android chưa khớp package hoặc SHA-1 của APK";
        }
        return message == null ? "lỗi xác thực" : message;
    }

    private void nativeSaved(String updatedAt) {
        runJs("window.nativeDriveSaved(" + JSONObject.quote(updatedAt) + ")");
    }

    private void nativeError(String message) {
        runJs("window.nativeDriveError(" + JSONObject.quote(message) + ")");
    }

    private void runJs(String script) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }
}

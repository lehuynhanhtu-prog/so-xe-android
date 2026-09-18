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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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
    private String exportBackupJson;
    private volatile String currentAccessToken;
    private String attachmentFolderId;
    private PendingSync pendingSync;

    private ActivityResultLauncher<IntentSenderRequest> authorizationLauncher;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;

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
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                uri -> {
                    String content = exportBackupJson;
                    exportBackupJson = null;
                    if (uri == null || content == null) {
                        runJs("window.nativeBackupResult(" + JSONObject.quote("Chưa lưu bản sao lưu.") + ")");
                        return;
                    }
                    String token = currentAccessToken;
                    ioExecutor.execute(() -> performBackup(token, content, uri));
                });
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        runJs("window.nativeBackupRestoreError(" + JSONObject.quote("Đã hủy khôi phục.") + ")");
                        return;
                    }
                    String token = currentAccessToken;
                    ioExecutor.execute(() -> performRestore(token, uri));
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
        public void restoreBackup() {
            if (currentAccessToken == null || currentAccessToken.isEmpty()) {
                runJs("window.nativeBackupRestoreError(" + JSONObject.quote(
                        "Hãy kết nối Google Drive trước khi khôi phục.") + ")");
                return;
            }
            runOnUiThread(() -> restoreLauncher.launch(new String[]{
                    "application/zip", "application/x-zip-compressed", "application/octet-stream"}));
        }

        @JavascriptInterface
        public void exportBackup(String json) {
            if (currentAccessToken == null || currentAccessToken.isEmpty()) {
                runJs("window.nativeBackupResult(" + JSONObject.quote(
                        "Hãy kết nối Google Drive trước khi sao lưu ảnh.") + ")");
                return;
            }
            runOnUiThread(() -> {
                exportBackupJson = json;
                backupLauncher.launch("so-xe-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".zip");
            });
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
            return "2.0.15";
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

    private static final class RestoreEntry {
        final String id, name, mimeType;
        final File file;
        RestoreEntry(String id, String name, String mimeType, File file) {
            this.id=id;this.name=name;this.mimeType=mimeType;this.file=file;
        }
    }

    private String uploadRestoredFile(String token, String folderId, RestoreEntry item)
            throws IOException, JSONException {
        String boundary="soxe-restore-"+System.currentTimeMillis();
        String type=item.mimeType.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")
                ? item.mimeType : "application/octet-stream";
        JSONObject meta=new JSONObject().put("name",item.name)
                .put("parents",new JSONArray().put(folderId));
        byte[] prefix=("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                +meta+"\r\n--"+boundary+"\r\nContent-Type: "+type+"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix=("\r\n--"+boundary+"--").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=(HttpURLConnection)new URL(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setRequestProperty("Content-Type","multipart/related; boundary="+boundary);
        connection.setFixedLengthStreamingMode((long)prefix.length+item.file.length()+suffix.length);
        connection.setDoOutput(true);
        try {
            try (OutputStream out=connection.getOutputStream();
                    InputStream input=new BufferedInputStream(new java.io.FileInputStream(item.file))) {
                out.write(prefix);
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1)out.write(buffer,0,count);
                out.write(suffix);
            }
            int status=connection.getResponseCode();
            String response=readAll(status>=200&&status<300
                    ?connection.getInputStream():connection.getErrorStream());
            if(status<200||status>=300)throw new HttpStatusException(status,driveErrorMessage(status,response));
            return new JSONObject(response).getString("id");
        }finally{connection.disconnect();}
    }

    private void relinkRecords(JSONObject restored, Map<String,String> ids)
            throws JSONException, IOException {
        for(String kind:new String[]{"cars","expenses"}){
            JSONArray records=restored.getJSONArray(kind);
            for(int i=0;i<records.length();i++){
                JSONArray attachments=records.getJSONObject(i).optJSONArray("attachments");
                if(attachments==null)continue;
                for(int j=0;j<attachments.length();j++){
                    JSONObject item=attachments.getJSONObject(j);
                    String old=item.optString("driveFileId");
                    if(!old.isEmpty()){
                        if(!ids.containsKey(old))throw new IOException("ZIP thiếu tệp đính kèm "+old);
                        item.put("driveFileId",ids.get(old));
                        item.remove("webViewLink");
                    }
                }
            }
        }
    }

    private void performRestore(String token, Uri uri) {
        File temp=new File(getCacheDir(),"soxe-restore-"+System.currentTimeMillis());
        Map<String,File> zipFiles=new HashMap<>();
        java.util.List<String> uploaded=new java.util.ArrayList<>();
        boolean savingDrive=false;
        try{
            if(token==null||token.isEmpty())throw new IOException("Hãy kết nối Google Drive trước khi khôi phục.");
            if(!temp.mkdirs())throw new IOException("Không tạo được thư mục tạm.");
            String json=null,manifestJson=null;
            long total=0;int count=0;
            try(InputStream source=getContentResolver().openInputStream(uri);
                    ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){
                ZipEntry entry;byte[] buffer=new byte[65536];
                while((entry=zip.getNextEntry())!=null){
                    if(entry.isDirectory()){zip.closeEntry();continue;}
                    String path=entry.getName();
                    if(!path.equals("so-xe-data.json")&&!path.equals("backup-manifest.json")
                            &&!path.startsWith("So xe/"))throw new IOException("ZIP chứa đường dẫn không hợp lệ.");
                    if(zipFiles.containsKey(path)||++count>10000)throw new IOException("ZIP có tệp trùng hoặc quá nhiều tệp.");
                    File staged=new File(temp,String.valueOf(count));
                    try(OutputStream output=new FileOutputStream(staged)){
                        int n;long size=0;
                        while((n=zip.read(buffer))!=-1){
                            size+=n;total+=n;
                            if(size>200L*1024*1024||total>2L*1024*1024*1024)
                                throw new IOException("ZIP vượt giới hạn dung lượng khôi phục.");
                            output.write(buffer,0,n);
                        }
                    }
                    zipFiles.put(path,staged);
                    if(path.equals("so-xe-data.json")||path.equals("backup-manifest.json")){
                        if(staged.length()>20L*1024*1024)throw new IOException("JSON quá lớn.");
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                        try(InputStream input=new java.io.FileInputStream(staged)){
                            int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
                        }
                        String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
                        if(path.equals("so-xe-data.json"))json=text;else manifestJson=text;
                    }
                    zip.closeEntry();
                }
            }
            if(json==null||manifestJson==null)throw new IOException("ZIP thiếu JSON hoặc danh sách tệp.");
            JSONObject restored=new JSONObject(json),manifest=new JSONObject(manifestJson);
            if(!restored.has("cars")||!restored.has("expenses")
                    ||!"so-xe-backup".equals(manifest.optString("format"))
                    ||manifest.optInt("version")!=1)throw new IOException("ZIP không phải bản sao lưu Sổ Xe hợp lệ.");
            JSONArray files=manifest.getJSONArray("files");
            Set<String> oldIds=new HashSet<>(),usedPaths=new HashSet<>();
            java.util.List<RestoreEntry> entries=new java.util.ArrayList<>();
            for(int i=0;i<files.length();i++){
                JSONObject item=files.getJSONObject(i);
                String id=item.getString("driveFileId"),path=item.getString("path"),name=item.getString("name");
                if(!path.startsWith("So xe/")||!oldIds.add(id)||!usedPaths.add(path)
                        ||!zipFiles.containsKey(path))throw new IOException("ZIP thiếu hoặc trùng tệp "+name);
                entries.add(new RestoreEntry(id,name,item.optString("mimeType"),zipFiles.get(path)));
            }
            // Verify references before making any changes to Drive.
            Map<String,String> expected=new HashMap<>();
            for(String id:oldIds)expected.put(id,id);
            relinkRecords(new JSONObject(json),expected);
            Map<String,String> ids=new HashMap<>();
            String folderId=entries.isEmpty()?null:findOrCreateAttachmentFolder(token);
            for(RestoreEntry item:entries){
                String fresh=uploadRestoredFile(token,folderId,item);
                uploaded.add(fresh);ids.put(item.id,fresh);
            }
            relinkRecords(restored,ids);
            String result=restored.toString();
            DriveFile remote=findDriveFile(token);
            savingDrive=true;
            if(remote==null)createDriveFile(token,result);
            else updateDriveFile(token,remote.id,result);
            uploaded.clear(); // New files now belong to the restored data.
            runJs("window.nativeBackupRestored("+JSONObject.quote(result)+")");
        }catch(Exception error){
            if(!savingDrive)for(String id:uploaded)try{
                http(token,"DELETE","https://www.googleapis.com/drive/v3/files/"
                        +URLEncoder.encode(id,"UTF-8"),null,null);
            }catch(Exception ignored){}
            if(error instanceof HttpStatusException
                    &&((HttpStatusException)error).status==401)clearInvalidToken(token);
            String message=error.getMessage()==null?"Không khôi phục được ZIP":error.getMessage();
            if(savingDrive)message+=" Hãy kiểm tra dữ liệu trên Drive trước khi thử lại.";
            runJs("window.nativeBackupRestoreError("+JSONObject.quote(message)+")");
        }finally{
            File[] staged=temp.listFiles();
            if(staged!=null)for(File file:staged)file.delete();
            temp.delete();
        }
    }

    private String backupFolder(String token) throws IOException, JSONException {
        String q = URLEncoder.encode("name='" + ATTACHMENT_FOLDER
                + "' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8");
        JSONObject result = new JSONObject(http(token, "GET",
                "https://www.googleapis.com/drive/v3/files?q=" + q
                        + "&spaces=drive&fields=files(id)&pageSize=100", null, null));
        JSONArray files = result.optJSONArray("files");
        return files == null || files.length() == 0 ? null : files.getJSONObject(0).getString("id");
    }

    private String backupPath(String id, String name) {
        String clean = name == null ? "" : name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (clean.isEmpty()) clean = "tep-dinh-kem";
        if (clean.length() > 120) clean = clean.substring(0, 120);
        return "So xe/" + id.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + clean;
    }

    private void putZipText(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void putDriveFile(ZipOutputStream zip, String token, String id, String path)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://www.googleapis.com/drive/v3/files/"
                        + URLEncoder.encode(id, "UTF-8") + "?alt=media").openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status,
                        driveErrorMessage(status, readAll(connection.getErrorStream())));
            }
            zip.putNextEntry(new ZipEntry(path));
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
            }
            zip.closeEntry();
        } finally {
            connection.disconnect();
        }
    }

    private void performBackup(String token, String json, Uri uri) {
        try {
            JSONObject manifest = new JSONObject().put("format", "so-xe-backup")
                    .put("version", 1)
                    .put("exportedAt", new java.util.Date().toString());
            JSONArray listed = new JSONArray();
            String folderId = backupFolder(token);
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Không mở được tệp sao lưu");
                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
                    putZipText(zip, "so-xe-data.json", json);
                    if (folderId != null) {
                        String page = "";
                        do {
                            String q = URLEncoder.encode("'" + folderId
                                    + "' in parents and trashed=false", "UTF-8");
                            String fields = URLEncoder.encode(
                                    "nextPageToken,files(id,name,mimeType,size)", "UTF-8");
                            String address = "https://www.googleapis.com/drive/v3/files?q=" + q
                                    + "&spaces=drive&fields=" + fields + "&pageSize=1000"
                                    + (page.isEmpty() ? "" : "&pageToken="
                                    + URLEncoder.encode(page, "UTF-8"));
                            JSONObject result = new JSONObject(
                                    http(token, "GET", address, null, null));
                            JSONArray files = result.optJSONArray("files");
                            if (files != null) for (int i = 0; i < files.length(); i++) {
                                JSONObject file = files.getJSONObject(i);
                                if ("application/vnd.google-apps.folder".equals(
                                        file.optString("mimeType"))) continue;
                                String id = file.getString("id");
                                String name = file.optString("name", "tep-dinh-kem");
                                String path = backupPath(id, name);
                                putDriveFile(zip, token, id, path);
                                listed.put(new JSONObject()
                                        .put("driveFileId", id).put("name", name)
                                        .put("path", path)
                                        .put("mimeType", file.optString("mimeType"))
                                        .put("size", file.optLong("size")));
                            }
                            page = result.optString("nextPageToken");
                        } while (!page.isEmpty());
                    }
                    manifest.put("files", listed);
                    putZipText(zip, "backup-manifest.json", manifest.toString(2));
                }
            }
            runJs("window.nativeBackupResult(" + JSONObject.quote(
                    "Đã lưu JSON và " + listed.length() + " tệp từ Drive vào ZIP.") + ")");
        } catch (Exception error) {
            if (error instanceof HttpStatusException
                    && ((HttpStatusException) error).status == 401) clearInvalidToken(token);
            runJs("window.nativeBackupResult(" + JSONObject.quote(
                    "Sao lưu thất bại: " + (error.getMessage() == null
                            ? "không đọc được tệp trên Drive" : error.getMessage())
                            + ". Không dùng tệp ZIP chưa hoàn chỉnh.") + ")");
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

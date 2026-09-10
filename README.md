# Sổ Xe Android và Web

Ứng dụng quản lý chi phí xe ô tô, theo dõi tiền xăng, bảo dưỡng, ODO, báo cáo L/100 km và đồng bộ dữ liệu qua Google Drive.

## Bản web trên GitHub Pages

https://lehuynhanhtu-prog.github.io/so-xe-android/

Mã web nằm trong thư mục `docs/`. Trong **Settings → Pages**, chọn:

- Source: **Deploy from a branch**
- Branch: **main**
- Folder: **/docs**

## Tạo APK

Workflow **Build So Xe APK** tự động tạo file `So-Xe-Android.apk` để cài trực tiếp trên Android. APK mở bản GitHub Pages ở địa chỉ trên.

Trước khi chuyển từ địa chỉ cũ, hãy đồng bộ dữ liệu lên Google Drive và tải thêm một bản sao JSON. Với Google OAuth Client loại **Web application**, thêm origin sau vào **Authorized JavaScript origins**:

`https://lehuynhanhtu-prog.github.io`

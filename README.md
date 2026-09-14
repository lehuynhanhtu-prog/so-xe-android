# Sổ Xe Android, Windows và Web

Ứng dụng quản lý chi phí ô tô: xe xăng/dầu và xe điện, ODO, nhiên liệu, sạc pin, bảo dưỡng, bảo hiểm TNDS và báo cáo.

## Android 2.0 — ứng dụng độc lập

Mã Android nằm trong `app/`. Toàn bộ HTML, CSS và JavaScript của ứng dụng được đóng gói tại `app/src/main/assets/www/` và chạy trong WebView nội bộ. APK không mở GitHub Pages và vẫn ghi chép được khi mất mạng.

Dữ liệu được lưu trong vùng riêng của ứng dụng. Khi người dùng cấp quyền, cầu native trong `MainActivity.java` đồng bộ tệp `so-xe-data.json` với thư mục `appDataFolder` trên Google Drive. Mọi thay đổi được gửi ngay; nếu mất mạng, trạng thái chưa đồng bộ được giữ lại để thử tiếp ở lần sau.

## Cấu hình OAuth Android

Sau lần build đầu tiên, mở Release mới nhất và tải `Android-OAuth-SHA1.txt`. Trong Google Cloud Console, tạo OAuth Client loại **Android** với:

- Package name: `vn.soxe.app`
- SHA-1: giá trị trong `Android-OAuth-SHA1.txt`

Google Drive API phải được bật trong cùng dự án Google Cloud. OAuth Web đang dùng cho GitHub Pages không thay thế được OAuth Android.

## Chuyển dữ liệu từ bản cũ

Trước khi cài bản 2.0, hãy mở bản web/bản Android cũ và chắc chắn trạng thái là **Đã đồng bộ Google Drive**. Bản 2.0 sẽ tải lại tệp Drive sau khi người dùng kết nối cùng tài khoản Google. Nên xuất thêm một bản JSON dự phòng.

## Bản web

https://lehuynhanhtu-prog.github.io/so-xe-android/

Mã GitHub Pages nằm trong `docs/` và tiếp tục hoạt động độc lập với APK.

## Bản cài Windows độc lập

Mã đóng gói Electron nằm trong `windows/`. Quy trình GitHub Actions tạo file `So-Xe-Windows-Setup-1.0.0.exe`, chứa toàn bộ giao diện và chức năng để sử dụng khi không có Internet. Dữ liệu được lưu bền vững tại vùng dữ liệu ứng dụng của Windows và không bị xóa khi cài bản cập nhật.

Bản Windows phục vụ ứng dụng tại địa chỉ nội bộ `http://127.0.0.1:18765`. Để Google Drive hoạt động, thêm địa chỉ này vào **Authorized JavaScript origins** của OAuth Client loại **Web application** đang dùng cho GitHub Pages.

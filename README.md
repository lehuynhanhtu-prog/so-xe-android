# Sổ Xe Android, Windows và Web

Ứng dụng quản lý chi phí ô tô: xe xăng/dầu, xe điện và xe Điện-Xăng, ODO, nhiên liệu, sạc pin, thuê pin, bảo dưỡng, phụ tùng, bảo hiểm TNDS, đăng kiểm, phí đường bộ và báo cáo.

## Android 2.0 — ứng dụng độc lập

Mã Android nằm trong `app/`. Toàn bộ HTML, CSS và JavaScript của ứng dụng được đóng gói tại `app/src/main/assets/www/` và chạy trong WebView nội bộ. APK không mở GitHub Pages và vẫn ghi chép được khi mất mạng.

Dữ liệu được lưu trong vùng riêng của ứng dụng. Khi người dùng cấp quyền, cầu native trong `MainActivity.java` đồng bộ tệp `so-xe-data.json` với thư mục `appDataFolder` trên Google Drive. Tệp đính kèm của giao dịch được lưu trong thư mục `Sổ xe` bằng phạm vi `drive.file`; ứng dụng chỉ truy cập các tệp do chính ứng dụng tạo. Mọi thay đổi được gửi ngay; nếu mất mạng, trạng thái chưa đồng bộ được giữ lại để thử tiếp ở lần sau.

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

## Bản Windows portable gọn nhẹ

Mã khởi chạy nằm trong `windows-portable/`. Quy trình GitHub Actions biên dịch bộ khởi chạy gọn nhẹ và tạo file `So-Xe-Windows-Portable-1.1.6.zip`. Người dùng chỉ cần giải nén rồi chạy `Chay-So-Xe.bat`; không cần PowerShell hay setup. Ứng dụng mở bằng trình duyệt mặc định để nhận sẵn các tài khoản Google đã lưu trong trình duyệt và vẫn sử dụng được khi không có Internet.

Nhật ký bộ khởi chạy được lưu tại `%APPDATA%\SoXeData`, bên ngoài thư mục giải nén. Dữ liệu cục bộ nằm trong hồ sơ của trình duyệt mặc định; dữ liệu dùng chung nằm trên Google Drive. Bản Windows phục vụ ứng dụng tại địa chỉ nội bộ `http://127.0.0.1:18765`. Để Google Drive hoạt động, thêm địa chỉ này vào **Authorized JavaScript origins** của OAuth Client loại **Web application** đang dùng cho GitHub Pages.

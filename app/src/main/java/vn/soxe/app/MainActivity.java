package vn.soxe.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://lehuynhanhtu-prog.github.io/so-xe-android/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLauncher();
        if (savedInstanceState == null) {
            findViewById(1002).postDelayed(this::openSoXe, 350);
        }
    }

    private void showLauncher() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(48, 48, 48, 48);
        box.setBackgroundColor(Color.rgb(18, 55, 42));

        TextView title = new TextView(this);
        title.setText("Sổ Xe");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Quản lý xăng, bảo dưỡng và chi phí ô tô");
        note.setTextColor(Color.rgb(207, 224, 216));
        note.setTextSize(16);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, 12, 0, 36);
        box.addView(note, noteParams);

        Button open = new Button(this);
        open.setId(1002);
        open.setText("MỞ SỔ XE");
        open.setTextSize(16);
        open.setOnClickListener(v -> openSoXe());
        box.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(box);
    }

    private void openSoXe() {
        Uri uri = Uri.parse(APP_URL);
        try {
            CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(Color.rgb(18, 55, 42))
                    .setNavigationBarColor(Color.rgb(18, 55, 42))
                    .build();
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(colors)
                    .setShowTitle(false)
                    .build();
            tabs.launchUrl(this, uri);
        } catch (ActivityNotFoundException firstError) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException secondError) {
                Toast.makeText(this, "Không tìm thấy trình duyệt. Hãy cài hoặc cập nhật Chrome.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, "Không thể mở Sổ Xe. Hãy kiểm tra Internet và thử lại.", Toast.LENGTH_LONG).show();
        }
    }
}

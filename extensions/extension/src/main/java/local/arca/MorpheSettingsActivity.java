package local.arca;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Separate, app-local settings page for the controls that can change at runtime. */
public final class MorpheSettingsActivity extends Activity {
    private int background;
    private int foreground;
    private int secondary;
    private int divider;

    public static void open(Context context) {
        Intent intent = new Intent(context, MorpheSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        MorphePrefs.init(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        background = dark ? 0xff101116 : 0xffffffff;
        foreground = dark ? 0xfff4f4f6 : 0xff202126;
        secondary = dark ? 0xffaeb0bb : 0xff666975;
        divider = dark ? 0xff31333b : 0xffe5e6eb;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(background);
        page.setFitsSystemWindows(true);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, foreground);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));
        back.setOnClickListener(v -> finish());
        TextView title = text("Morphe 설정", 22, foreground);
        title.setTypeface(null, Typeface.BOLD);
        toolbar.addView(title);
        page.addView(toolbar);
        line(page);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), dp(24));
        section(body, "광고");
        toggle(body, "앱 광고 요청·영역 제거",
                "앱 자체의 이미지 광고와 빈 영역을 숨깁니다. 이용자 광고 글과 텍스트 광고는 유지합니다.",
                MorphePrefs.blockAds(), MorphePrefs::setBlockAds);
        section(body, "패치 시 적용되는 기능");
        info(body, "분석 수집·백그라운드 작업 축소",
                "Morphe에서 이 패치를 선택한 경우 설치 시 적용됩니다. 변경하려면 다시 빌드하세요.");
        info(body, "미디어 저장 스트리밍",
                "Morphe에서 이 패치를 선택한 경우 이미지·GIF·동영상 저장 시 작은 버퍼를 사용합니다. 재생과 미리보기에는 적용되지 않습니다.");
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
    }

    private interface Change { void set(boolean value); }

    private void section(LinearLayout parent, String label) {
        TextView view = text(label, 14, secondary);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(26), 0, dp(8));
        parent.addView(view);
    }

    private void toggle(LinearLayout parent, String title, String description,
                        boolean checked, Change change) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout labels = labels(title, description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(title);
        toggle.setOnCheckedChangeListener((button, value) -> change.set(value));
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row);
        line(parent);
    }

    private void info(LinearLayout parent, String title, String description) {
        LinearLayout row = labels(title, description);
        row.setPadding(0, dp(14), 0, dp(14));
        parent.addView(row);
        line(parent);
    }

    private LinearLayout labels(String title, String description) {
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(0, 0, dp(16), 0);
        labels.addView(text(title, 17, foreground));
        TextView detail = text(description, 13, secondary);
        detail.setPadding(0, dp(6), 0, 0);
        labels.addView(detail);
        return labels;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private void line(LinearLayout parent) {
        View view = new View(this);
        view.setBackgroundColor(divider);
        parent.addView(view, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

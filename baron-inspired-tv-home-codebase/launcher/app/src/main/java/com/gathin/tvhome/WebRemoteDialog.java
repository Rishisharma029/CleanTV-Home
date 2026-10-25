package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Fullscreen frosted modal displaying the scannable Web Remote QR Code and URL.
 */
public final class WebRemoteDialog {

    private WebRemoteDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        // Ensure server is started
        WebRemoteServer.start(activity);

        String ip = WebRemoteServer.getLocalIpAddress(activity);
        int port = WebRemoteServer.getActivePort();
        boolean isEmu = WebRemoteServer.isEmulator() || "10.0.2.15".equals(ip);

        String url = WebRemoteServer.getServerUrl(activity);
        Bitmap qrBitmap = QRCodeGenerator.generate(url, 400);

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_web_remote, root, false);
        ImageView qrImage = dialogView.findViewById(R.id.qrCodeImage);
        TextView urlText = dialogView.findViewById(R.id.qrUrlText);
        TextView pcUrlText = dialogView.findViewById(R.id.qrPcUrlText);
        TextView hintText = dialogView.findViewById(R.id.qrHintText);
        TextView modeBadge = dialogView.findViewById(R.id.qrModeBadge);
        Button btnClose = dialogView.findViewById(R.id.btnCloseQr);

        if (qrImage != null && qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        }
        if (urlText != null) {
            urlText.setText(url);
        }

        if (isEmu) {
            if (modeBadge != null) {
                modeBadge.setText("💻 Android Emulator Mode");
                modeBadge.setTextColor(0xFF38BDF8);
            }
            if (pcUrlText != null) {
                pcUrlText.setVisibility(View.VISIBLE);
                pcUrlText.setText("💻 Open in PC Browser: http://localhost:" + port);
            }
            if (hintText != null) {
                hintText.setText("Open http://localhost:" + port + " in Chrome on this PC to test!\nFor phone scan: Ensure phone is on same Wi-Fi (" + ip + ":" + port + ")");
            }
        } else {
            if (modeBadge != null) {
                modeBadge.setText("⚡ TV Local Wi-Fi Active");
                modeBadge.setTextColor(0xFF34D399);
            }
            if (pcUrlText != null) {
                pcUrlText.setVisibility(View.GONE);
            }
            if (hintText != null) {
                hintText.setText("Connect phone to the same Wi-Fi network to control this TV");
            }
        }

        Runnable dismiss = () -> {
            if (dialogView.getParent() instanceof ViewGroup) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
        };

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss.run());
            btnClose.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    dismiss.run();
                    return true;
                }
                return false;
            });
        }

        dialogView.setOnClickListener(v -> dismiss.run());
        dialogView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss.run();
                return true;
            }
            return false;
        });

        root.addView(dialogView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (btnClose != null) {
            btnClose.requestFocus();
        }
    }
}

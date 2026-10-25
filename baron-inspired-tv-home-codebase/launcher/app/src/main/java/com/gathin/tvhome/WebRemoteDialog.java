package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * TV Modal Dialog displaying the scannable Web Remote QR Code and URL.
 * Uses a modal Dialog window to trap TV remote focus inside the dialog,
 * provides visible glowing focus states, and dismisses reliably via Done button,
 * Back key, or clicking outside.
 */
public final class WebRemoteDialog {

    private WebRemoteDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        // Ensure server is started
        WebRemoteServer.start(activity);

        String ip = WebRemoteServer.getLocalIpAddress(activity);
        int port = WebRemoteServer.getActivePort();
        boolean isEmu = WebRemoteServer.isEmulator() || "10.0.2.15".equals(ip);

        String url = WebRemoteServer.getServerUrl(activity);
        Bitmap qrBitmap = QRCodeGenerator.generate(url, 400);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_web_remote, null);
        dialog.setContentView(dialogView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        ImageView qrImage = dialogView.findViewById(R.id.qrCodeImage);
        TextView urlText = dialogView.findViewById(R.id.qrUrlText);
        TextView pcUrlText = dialogView.findViewById(R.id.qrPcUrlText);
        TextView hintText = dialogView.findViewById(R.id.qrHintText);
        TextView modeBadge = dialogView.findViewById(R.id.qrModeBadge);
        Button btnClose = dialogView.findViewById(R.id.btnCloseQr);
        View rootOverlay = dialogView.findViewById(R.id.qrDialogRoot);
        View cardContent = dialogView.findViewById(R.id.qrCardContent);

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

        // Backdrop click dismisses
        if (rootOverlay != null) {
            rootOverlay.setOnClickListener(v -> dialog.dismiss());
        }
        // Card content consumes click so tapping inside doesn't dismiss accidentally
        if (cardContent != null) {
            cardContent.setOnClickListener(v -> {});
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
            btnClose.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                    v.playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            });
            btnClose.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        dialog.dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        dialog.dismiss();
                        return true;
                    }
                }
                return false;
            });
        }

        dialog.setOnShowListener(d -> {
            if (btnClose != null) {
                btnClose.requestFocus();
            }
        });

        dialog.show();
    }
}

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

        String url = WebRemoteServer.getServerUrl(activity);
        Bitmap qrBitmap = QRCodeGenerator.generate(url, 400);

        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_web_remote, root, false);
        ImageView qrImage = dialogView.findViewById(R.id.qrCodeImage);
        TextView urlText = dialogView.findViewById(R.id.qrUrlText);
        Button btnClose = dialogView.findViewById(R.id.btnCloseQr);

        if (qrImage != null && qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        }
        if (urlText != null) {
            urlText.setText(url);
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

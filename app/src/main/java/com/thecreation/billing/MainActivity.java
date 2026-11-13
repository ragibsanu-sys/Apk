package com.thecreation.billing;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage: saved clients & reminder message
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("file:")) return false;
                openExternal(url);
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/index.html");
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "App not found on this phone", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the link", Toast.LENGTH_LONG).show();
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() { public void run() { openExternal(url); } });
        }

        @JavascriptInterface
        public void sharePdf(final String b64, final String name) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        File dir = new File(getCacheDir(), "pdfs");
                        if (!dir.exists()) dir.mkdirs();
                        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                        File f = new File(dir, safe);
                        FileOutputStream out = new FileOutputStream(f);
                        out.write(Base64.decode(b64, Base64.DEFAULT));
                        out.close();
                        Uri uri = FileProvider.getUriForFile(MainActivity.this,
                                getPackageName() + ".fileprovider", f);
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("application/pdf");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Share Bill PDF"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not share the PDF", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        // close the PDF preview first, otherwise leave the app
        web.evaluateJavascript(
            "(function(){var p=document.getElementById('previewBox');" +
            "if(p&&p.style.display==='block'){closePreview();return 1;}return 0;})()",
            new android.webkit.ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    if (!"1".equals(v)) MainActivity.super.onBackPressed();
                }
            });
    }
}

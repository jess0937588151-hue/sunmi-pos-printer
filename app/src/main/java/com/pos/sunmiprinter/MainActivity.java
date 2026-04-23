package com.pos.sunmiprinter;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import woyou.aidlservice.jiuiv5.IWoyouService;
import woyou.aidlservice.jiuiv5.ICallback;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private IWoyouService printerService;
    private static final String TAG = "SunmiPOS";

    // 你的 POS 網站網址，請替換成實際的
    private static final String POS_URL = "https://your-pos-website.com";

    private ServiceConnection connPrinter = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            Log.d(TAG, "Printer service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            Log.d(TAG, "Printer service disconnected");
        }
    };

    private ICallback printerCallback = new ICallback.Stub() {
        @Override
        public void onRunResult(boolean isSuccess) throws RemoteException {}
        @Override
        public void onReturnString(String result) throws RemoteException {}
        @Override
        public void onRaiseException(int code, String msg) throws RemoteException {
            Log.e(TAG, "Printer error: " + code + " - " + msg);
        }
        @Override
        public void onPrintResult(int code, String msg) throws RemoteException {}
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // 注入 JS Bridge
        webView.addJavascriptInterface(new PrinterBridge(), "SunmiPrinter");

        webView.loadUrl(POS_URL);

        // 綁定 SUNMI 印表機服務
        bindPrinterService();
    }

    private void bindPrinterService() {
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        bindService(intent, connPrinter, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unbindService(connPrinter); } catch (Exception e) {}
    }

    // JS Bridge - 網頁可呼叫的方法
    public class PrinterBridge {

        @JavascriptInterface
        public boolean isConnected() {
            return printerService != null;
        }

        @JavascriptInterface
        public void printText(String text, int fontSize, boolean isBold) {
            if (printerService == null) return;
            try {
                printerService.setAlignment(0, printerCallback);
                printerService.printTextWithFont(text + "\n",
                    "", fontSize, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "printText error", e);
            }
        }

        @JavascriptInterface
        public void printTextCenter(String text, int fontSize, boolean isBold) {
            if (printerService == null) return;
            try {
                printerService.setAlignment(1, printerCallback);
                printerService.printTextWithFont(text + "\n",
                    "", fontSize, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "printTextCenter error", e);
            }
        }

        @JavascriptInterface
        public void printLine() {
            if (printerService == null) return;
            try {
                printerService.setAlignment(0, printerCallback);
                printerService.printTextWithFont(
                    "------------------------------------------------\n",
                    "", 24, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "printLine error", e);
            }
        }

        @JavascriptInterface
        public void printRow(String left, String right, int fontSize) {
            if (printerService == null) return;
            try {
                String[] texts = {left, right};
                int[] widths = {1, 1};
                int[] aligns = {0, 2};
                printerService.printColumnsString(texts, widths, aligns, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "printRow error", e);
            }
        }

        @JavascriptInterface
        public void printThreeColumns(String left, String center, String right) {
            if (printerService == null) return;
            try {
                String[] texts = {left, center, right};
                int[] widths = {2, 1, 1};
                int[] aligns = {0, 1, 2};
                printerService.printColumnsString(texts, widths, aligns, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "printThreeColumns error", e);
            }
        }

        @JavascriptInterface
        public void feedAndCut() {
            if (printerService == null) return;
            try {
                printerService.lineWrap(4, printerCallback);
                printerService.cutPaper(printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "feedAndCut error", e);
            }
        }

        @JavascriptInterface
        public void openCashDrawer() {
            if (printerService == null) return;
            try {
                printerService.sendRAWData(new byte[]{0x1B, 0x70, 0x00, 0x19, (byte)0xFA}, printerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "openCashDrawer error", e);
            }
        }

        @JavascriptInterface
        public void printReceipt(String jsonData) {
            if (printerService == null) {
                Log.e(TAG, "Printer not connected");
                return;
            }
            try {
                org.json.JSONObject data = new org.json.JSONObject(jsonData);

                // 店名
                printerService.setAlignment(1, printerCallback);
                printerService.printTextWithFont(
                    data.optString("shopName", "商店") + "\n",
                    "", 32, printerCallback);

                // 副標題
                if (data.has("subtitle")) {
                    printerService.printTextWithFont(
                        data.getString("subtitle") + "\n",
                        "", 24, printerCallback);
                }

                // 分隔線
                printerService.setAlignment(0, printerCallback);
                printerService.printTextWithFont(
                    "------------------------------------------------\n",
                    "", 24, printerCallback);

                // 訂單資訊
                if (data.has("orderNumber")) {
                    printerService.printTextWithFont(
                        "單號: " + data.getString("orderNumber") + "\n",
                        "", 24, printerCallback);
                }
                if (data.has("dateTime")) {
                    printerService.printTextWithFont(
                        "時間: " + data.getString("dateTime") + "\n",
                        "", 24, printerCallback);
                }
                if (data.has("orderType")) {
                    printerService.printTextWithFont(
                        "類型: " + data.getString("orderType") + "\n",
                        "", 24, printerCallback);
                }

                // 分隔線
                printerService.printTextWithFont(
                    "------------------------------------------------\n",
                    "", 24, printerCallback);

                // 品項
                if (data.has("items")) {
                    org.json.JSONArray items = data.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject item = items.getJSONObject(i);
                        String name = item.optString("name", "");
                        int qty = item.optInt("qty", 1);
                        int price = item.optInt("price", 0);

                        String[] texts = {name, "x" + qty, "$" + price};
                        int[] widths = {2, 1, 1};
                        int[] aligns = {0, 1, 2};
                        printerService.printColumnsString(texts, widths, aligns, printerCallback);

                        // 品項備註
                        if (item.has("options") && item.getString("options").length() > 0) {
                            printerService.printTextWithFont(
                                "  " + item.getString("options") + "\n",
                                "", 20, printerCallback);
                        }
                        if (item.has("note") && item.getString("note").length() > 0) {
                            printerService.printTextWithFont(
                                "  *" + item.getString("note") + "\n",
                                "", 20, printerCallback);
                        }
                    }
                }

                // 分隔線
                printerService.printTextWithFont(
                    "------------------------------------------------\n",
                    "", 24, printerCallback);

                // 合計
                if (data.has("total")) {
                    printerService.setAlignment(2, printerCallback);
                    printerService.printTextWithFont(
                        "合計: $" + data.getString("total") + "\n",
                        "", 32, printerCallback);
                }

                // 付款方式
                if (data.has("paymentMethod")) {
                    printerService.setAlignment(0, printerCallback);
                    printerService.printTextWithFont(
                        "付款: " + data.getString("paymentMethod") + "\n",
                        "", 24, printerCallback);
                }

                // 頁尾
                printerService.setAlignment(1, printerCallback);
                printerService.printTextWithFont(
                    "\n謝謝光臨\n",
                    "", 24, printerCallback);

                // 走紙 + 切紙
                printerService.lineWrap(4, printerCallback);
                printerService.cutPaper(printerCallback);

            } catch (Exception e) {
                Log.e(TAG, "printReceipt error", e);
            }
        }
    }
}

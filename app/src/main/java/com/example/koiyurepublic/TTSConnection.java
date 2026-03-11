package com.example.koiyurepublic;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/**
 * TTSConnection
 *
 * Android TextToSpeech のラッパー。SpinalCord から生成・破棄される。
 *
 * 特徴:
 *   - 初期化完了前に speak() を呼んでも内部キューに溜めて後で読む
 *   - 読み上げON/OFFを setEnabled() で動的に切り替え可能
 *   - speak() はキューに積むだけなのでスレッドセーフ
 *   - shutdown() で TTS エンジンを解放する（SpinalCord.onDestroy() で呼ぶこと）
 */
public class TTSConnection {

    private static final String TAG = "TTSConnection";

    private TextToSpeech tts;
    private boolean initialized = false;
    private boolean enabled     = true;  // ユーザー設定でON/OFF切替

    // 初期化完了前に積まれたメッセージを保持するキュー
    private final Queue<String> pendingQueue = new ArrayDeque<>();

    // ──────────────────────────────────────────────
    //  初期化 / 破棄
    // ──────────────────────────────────────────────

    public TTSConnection(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.JAPANESE);
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "日本語TTSが利用不可 — フォールバックなし");
                }

                // 読み上げ完了ログ（将来的にUI通知と連携可能）
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS 開始: " + utteranceId);
                    }
                    @Override public void onDone(String utteranceId) {
                        Log.d(TAG, "TTS 完了: " + utteranceId);
                    }
                    @Override public void onError(String utteranceId) {
                        Log.w(TAG, "TTS エラー: " + utteranceId);
                    }
                });

                initialized = true;
                Log.d(TAG, "TTS 初期化完了");

                // 溜まっていたメッセージを順番に読む
                flushPendingQueue();
            } else {
                Log.e(TAG, "TTS 初期化失敗 status=" + status);
            }
        });
    }

    /** TTS エンジンを解放する。SpinalCord.onDestroy() から呼ぶこと */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            initialized = false;
            Log.d(TAG, "TTS shutdown");
        }
    }

    // ──────────────────────────────────────────────
    //  読み上げ
    // ──────────────────────────────────────────────

    /**
     * テキストを読み上げる。
     * 初期化完了前に呼ばれた場合は内部キューに積み、完了後に自動で読む。
     * enabled=false の場合は何もしない。
     *
     * @param text 読み上げるテキスト
     */
    public void speak(String text) {
        if (!enabled) return;
        if (!initialized) {
            pendingQueue.offer(text);
            Log.d(TAG, "TTS未初期化 → キューに積む: " + text);
            return;
        }
        doSpeak(text);
    }

    /**
     * 現在の読み上げを中断して即座に読む（緊急地震速報など優先度の高い情報向け）。
     */
    public void speakNow(String text) {
        if (!enabled) return;
        if (!initialized) {
            pendingQueue.clear();       // 緊急なので旧キューを破棄
            pendingQueue.offer(text);
            return;
        }
        tts.stop();                     // 現在の読み上げを中断
        doSpeak(text);
    }

    private void doSpeak(String text) {
        if (tts == null) return;
        String uid = "utt_" + System.currentTimeMillis();
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, uid);
    }

    private void flushPendingQueue() {
        while (!pendingQueue.isEmpty()) {
            doSpeak(pendingQueue.poll());
        }
    }

    // ──────────────────────────────────────────────
    //  設定
    // ──────────────────────────────────────────────

    /** TTS のON/OFFを切り替える。OFFにすると読み上げをスキップする */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && tts != null) tts.stop();
        Log.d(TAG, "TTS enabled=" + enabled);
    }

    public boolean isEnabled() { return enabled; }

    /**
     * 読み上げ速度を設定する（デフォルト1.0）
     * @param rate 0.5=遅い  1.0=普通  2.0=速い
     */
    public void setSpeechRate(float rate) {
        if (tts != null) tts.setSpeechRate(rate);
    }

    /**
     * ピッチを設定する（デフォルト1.0）
     * こいしちゃんらしく少し高めにする場合は 1.2〜1.4 くらいを推奨
     * @param pitch 値が大きいほど高い声
     */
    public void setPitch(float pitch) {
        if (tts != null) tts.setPitch(pitch);
    }
}

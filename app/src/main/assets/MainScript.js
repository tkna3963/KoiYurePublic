// ========================================
// グローバル変数
// ========================================
let AllWebsocketData = [];
let timeUpdateInterval = null;

// ユーザー位置情報を保存
let userLocation = null;

// ========================================
// DOM要素のキャッシュ
// ========================================
const elements = {};

function cacheElements() {
    elements.nowTime           = document.getElementById('now_time');
    elements.textarea          = document.getElementById('maintextarea');
    elements.pagebar           = document.getElementById('pagebar');
    elements.menu              = document.getElementById('menu');
    elements.infomenu          = document.getElementById('infomenu');
    elements.toggleMenuBtn     = document.getElementById('toggleMenuBtn');
    elements.toggleInfoMenuBtn = document.getElementById('toggleinfoMenuBtn');
    elements.mapChangeBtn      = document.getElementById('MapChange');
    elements.backImg           = document.getElementById('back');
    elements.koisiImg          = document.getElementById('koisi');
    elements.leafletMap        = document.getElementById('LeafletMap');
    elements.koisiarrow        = document.querySelector('.koisiarrow_box');
    elements.p2pIndicator      = document.getElementById('p2pStatusIndicator');
    elements.p2pText           = document.getElementById('p2pStatusText');
    elements.nowLocate         = document.getElementById('NowLocate');

    // Service制御ボタン
    elements.startBtn          = document.getElementById('startServiceBtn');
    elements.stopBtn           = document.getElementById('stopServiceBtn');

    // TTS / 通知トグル
    elements.ttsToggle         = document.getElementById('ttsToggle');
    elements.notifToggle       = document.getElementById('notifToggle');

    // こいしの吹き出しテキスト
    elements.koishiText        = document.getElementById('koishiText');
}

// ========================================
// AndroidBridge ヘルパー
// （JavascriptInterface名: AndroidBridge）
// ========================================
const Bridge = {
    available() {
        return typeof AndroidBridge !== 'undefined' && AndroidBridge !== null;
    },
    isRunning()             { return this.available() ? AndroidBridge.isServiceRunning()   : false; },
    start()                 { if (this.available()) AndroidBridge.startBackground(); },
    stop()                  { if (this.available()) AndroidBridge.stopBackground(); },
    log(msg)                { if (this.available()) AndroidBridge.log(msg); },
    setTts(enabled)         { if (this.available() && AndroidBridge.setTtsEnabled)         AndroidBridge.setTtsEnabled(enabled); },
    setNotif(enabled)       { if (this.available() && AndroidBridge.setNotificationEnabled) AndroidBridge.setNotificationEnabled(enabled); },
};

// ========================================
// Java → HTML コールバック（SpinalCord → MainActivity → WebView）
// ========================================

/**
 * WebSocket受信データ（JSON文字列）
 * SpinalCord.onMessage → UICallback.onEarthquakeMessage → MainActivity.runJs → ここ
 */
window.onEarthquakeData = function (jsonStr) {
    Bridge.log('[onEarthquakeData] ' + jsonStr.substring(0, 60));
    try {
        const data = JSON.parse(jsonStr);
        onP2PMessage(data);
    } catch (e) {
        console.error('onEarthquakeData parse error:', e);
    }
};

/**
 * WebSocket接続状態の変化
 * SpinalCord.onConnected / onDisconnected → UICallback → MainActivity → ここ
 * @param {boolean} connected
 * @param {boolean} willReconnect
 */
window.onConnectionStateChanged = function (connected, willReconnect) {
    Bridge.log('[onConnectionStateChanged] connected=' + connected + ' willReconnect=' + willReconnect);
    updateConnectionStatus(
        elements.p2pIndicator,
        elements.p2pText,
        connected,
        willReconnect
    );
};

/**
 * Service起動・停止の状態変化
 * ServiceConnection.onServiceConnected / onServiceDisconnected → MainActivity → ここ
 * @param {boolean} running
 */
window.onServiceStateChanged = function (running) {
    Bridge.log('[onServiceStateChanged] running=' + running);
    syncServiceButtons(running);

    // Service停止中は接続状態も切断表示に
    if (!running) {
        updateConnectionStatus(elements.p2pIndicator, elements.p2pText, false, false);
    }
};

// ========================================
// 時計を動かす
// ========================================
function updateTime() {
    const now = new Date();
    const formatted =
        `${now.getFullYear()}/${String(now.getMonth() + 1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')} ` +
        `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
    if (elements.nowTime) elements.nowTime.textContent = formatted;
}

// ========================================
// メニュー開閉
// ========================================
function toggleMenu(menuElement, btnElement) {
    const isOpen = menuElement.classList.toggle('open');
    if (btnElement) btnElement.setAttribute('aria-expanded', isOpen);
}

// ========================================
// テキストエリア更新
// ========================================
function updateMainTextarea(index) {
    if (!elements.textarea || index < 0 || index >= AllWebsocketData.length) return;

    let data = AllWebsocketData[index];

    if (typeof data === 'string') {
        try { data = JSON.parse(data); }
        catch (e) { elements.textarea.value = '(データの読み込みに失敗しました)'; return; }
    }

    if (data.code && typeof formatToTelop === 'function') {
        elements.textarea.value = formatToTelop(data);
    } else if (data.type && typeof wolfxcoverter === 'function') {
        elements.textarea.value = wolfxcoverter(data) ?? '(空のメッセージ)';
    } else {
        elements.textarea.value = '(表示できる情報がありません)';
    }
}

// ========================================
// ページバー更新
// ========================================
function updatePagebar() {
    if (!elements.pagebar) return;
    const maxIndex = Math.max(0, AllWebsocketData.length - 1);
    elements.pagebar.max   = maxIndex;
    elements.pagebar.value = maxIndex;
}

// ========================================
// WebSocketメッセージ受信
// ========================================
function onP2PMessage(msg) {
    AllWebsocketData.push(msg);
    updatePagebar();
    if (typeof P2PMap === 'function') P2PMap(msg);
    updateMainTextarea(AllWebsocketData.length - 1);

    // こいしの吹き出しに概要を表示
    updateKoishiSpeech(msg);
}

function onWolfxMessage(msg) {
    AllWebsocketData.push(msg);
    updatePagebar();
    updateMainTextarea(AllWebsocketData.length - 1);
}

// ========================================
// こいしの吹き出し更新
// ========================================
function updateKoishiSpeech(data) {
    if (!elements.koishiText && !elements.koisiarrow) return;

    const target = elements.koishiText || elements.koisiarrow;
    const code   = data.code;

    let msg = '';
    if (code === 556) {
        const hypo = data.earthquake?.hypocenter;
        msg = `⚡ 緊急地震速報！${hypo?.name ?? ''}`;
    } else if (code === 551) {
        const quake = data.earthquake;
        const maxScale = quake?.maxScale;
        const scaleText = scaleToText(maxScale);
        msg = `地震情報 最大震度${scaleText}`;
    } else if (code === 552) {
        msg = data.cancelled ? '津波予報が解除されました' : '⚠️ 津波予報が発表されました';
    } else if (code === 554) {
        msg = '⚡ 緊急地震速報を検出しました';
    } else if (code === 555) {
        // ピア情報は吹き出しに出さない
        return;
    } else if (code === 561) {
        msg = '地震感知情報を受信しました';
    } else if (code === 9611) {
        msg = '地震感知 解析結果を受信';
    }

    if (msg) {
        target.textContent = msg;
        // 5秒後にデフォルトに戻す
        clearTimeout(target._speechTimer);
        target._speechTimer = setTimeout(() => {
            target.textContent = '古明地こいしだよ!!';
        }, 5000);
    }
}

/** 震度コード → 表示文字列（P2PConverts.scaleToText と同等） */
function scaleToText(scale) {
    const map = { 10:'1', 20:'2', 30:'3', 40:'4', 45:'5弱', 46:'5弱以上(推定)',
                  50:'5強', 55:'6弱', 60:'6強', 70:'7' };
    return map[scale] ?? '不明';
}

// ========================================
// 接続状態表示更新
// ========================================
function updateConnectionStatus(indicator, textElement, isConnected, willReconnect = false) {
    if (!indicator || !textElement) return;

    indicator.classList.toggle('status-connected',    isConnected);
    indicator.classList.toggle('status-disconnected', !isConnected);
    indicator.classList.toggle('status-reconnecting', !isConnected && willReconnect);

    if (isConnected) {
        textElement.textContent = '接続中';
    } else if (willReconnect) {
        textElement.textContent = '再接続中…';
    } else {
        textElement.textContent = '切断';
    }
}

// ========================================
// Service ボタン同期
// ========================================
function syncServiceButtons(running) {
    if (elements.startBtn) elements.startBtn.disabled = running;
    if (elements.stopBtn)  elements.stopBtn.disabled  = !running;

    // ボタンテキスト・スタイルを状態に合わせて更新
    if (elements.startBtn) elements.startBtn.setAttribute('aria-pressed', String(!running));
    if (elements.stopBtn)  elements.stopBtn.setAttribute('aria-pressed',  String(running));
}

// ========================================
// TTS / 通知トグル
// ========================================
function setupToggle(toggleEl, getter, setter) {
    if (!toggleEl) return;
    // 初期状態を反映
    const initial = getter();
    toggleEl.checked = initial;

    toggleEl.addEventListener('change', () => {
        setter(toggleEl.checked);
        Bridge.log('[toggle] ' + toggleEl.id + '=' + toggleEl.checked);
    });
}

// ========================================
// 位置情報取得
// ========================================
function getUserLocation() {
    if (!navigator.geolocation) return;

    navigator.geolocation.getCurrentPosition(
        function (position) {
            const { latitude, longitude } = position.coords;

            fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}`)
                .then(r => r.json())
                .then(data => {
                    const place = data.address.city || data.address.town || data.address.village || '不明な地域';
                    userLocation = { lat: latitude, lon: longitude, place };

                    if (elements.nowLocate) {
                        elements.nowLocate.textContent = `${place} (${latitude.toFixed(1)}, ${longitude.toFixed(1)})`;
                    }
                    if (typeof addUserLocationMarker === 'function') addUserLocationMarker();
                })
                .catch(err => {
                    console.error('逆ジオコーディングに失敗:', err);
                    if (elements.nowLocate) elements.nowLocate.textContent = '位置情報の取得に失敗';
                });
        },
        function (error) {
            const msgs = {
                [error.PERMISSION_DENIED]:    '位置情報の許可が必要です',
                [error.POSITION_UNAVAILABLE]: '位置情報が利用できません',
                [error.TIMEOUT]:              '位置情報の取得がタイムアウト',
            };
            if (elements.nowLocate) elements.nowLocate.textContent = msgs[error.code] ?? '不明なエラー';
        }
    );
}

// ========================================
// アプリ初期化
// ========================================
function initializeApp() {
    cacheElements();

    updateTime();
    timeUpdateInterval = setInterval(updateTime, 1000);

    // メニュー開閉
    if (elements.toggleMenuBtn && elements.menu) {
        elements.toggleMenuBtn.addEventListener('click', () => toggleMenu(elements.menu, elements.toggleMenuBtn));
    }
    if (elements.toggleInfoMenuBtn && elements.infomenu) {
        elements.toggleInfoMenuBtn.addEventListener('click', () => toggleMenu(elements.infomenu, elements.toggleInfoMenuBtn));
    }

    // ページバー
    if (elements.pagebar) {
        elements.pagebar.addEventListener('input', e => updateMainTextarea(parseInt(e.target.value, 10)));
    }

    // 地図切替
    if (elements.mapChangeBtn) {
        elements.mapChangeBtn.addEventListener('click', toggleMapMode);
    }

    // ──────────────────────────────────
    // AndroidBridge 連携
    // ──────────────────────────────────
    if (Bridge.available()) {
        // 初期状態を反映
        const running = Bridge.isRunning();
        syncServiceButtons(running);
        updateConnectionStatus(elements.p2pIndicator, elements.p2pText, running);

        // Service起動ボタン
        if (elements.startBtn) {
            elements.startBtn.addEventListener('click', () => {
                Bridge.start();
                // onServiceStateChanged が呼ばれるまでの間、ボタンを無効化
                elements.startBtn.disabled = true;
            });
        }

        // Service停止ボタン
        if (elements.stopBtn) {
            elements.stopBtn.addEventListener('click', () => {
                Bridge.stop();
                elements.stopBtn.disabled = true;
            });
        }

        // TTS トグル
        setupToggle(
            elements.ttsToggle,
            () => AndroidBridge.isTtsEnabled ? AndroidBridge.isTtsEnabled() : true,
            v  => AndroidBridge.setTtsEnabled ? AndroidBridge.setTtsEnabled(v) : null
        );

        // 通知トグル
        setupToggle(
            elements.notifToggle,
            () => AndroidBridge.isNotificationEnabled ? AndroidBridge.isNotificationEnabled() : true,
            v  => AndroidBridge.setNotificationEnabled ? AndroidBridge.setNotificationEnabled(v) : null
        );

    } else {
        // Android外（ブラウザ等）での動作
        console.warn('AndroidBridge is not available — running in browser mode');
        if (elements.p2pText) elements.p2pText.textContent = 'Android API 利用不可';
        if (elements.startBtn) elements.startBtn.disabled = false;
        if (elements.stopBtn)  elements.stopBtn.disabled  = true;
    }

    // 位置情報
    getUserLocation();
}

// ========================================
// ページ読み込み時に実行
// ========================================
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

window.addEventListener('beforeunload', () => {
    if (timeUpdateInterval) clearInterval(timeUpdateInterval);
});
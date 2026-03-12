// ========================================
// グローバル変数
// ========================================
let AllWebsocketData = [];
let timeUpdateInterval = null;
let userLocation = null;
let currentIndex = -1; // 現在表示中のインデックス

// ========================================
// DOM要素のキャッシュ
// ========================================
const elements = {};

function cacheElements() {
    elements.nowTime           = document.getElementById('now_time');
    elements.textarea          = document.getElementById('maintextarea');
    elements.pagebar           = document.getElementById('pagebar');
    elements.pagebarLabel      = document.getElementById('pagebarLabel');
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

    elements.startBtn          = document.getElementById('startServiceBtn');
    elements.stopBtn           = document.getElementById('stopServiceBtn');
    elements.ttsToggle         = document.getElementById('ttsToggle');
    elements.notifToggle       = document.getElementById('notifToggle');
    elements.koishiText        = document.getElementById('koishiText');

    // 追加要素
    elements.dataCountBadge    = document.getElementById('dataCountBadge');
    elements.prevBtn           = document.getElementById('prevBtn');
    elements.nextBtn           = document.getElementById('nextBtn');
    elements.latestBtn         = document.getElementById('latestBtn');
    elements.codeFilterBtns    = document.querySelectorAll('.code-filter-btn');
    elements.infomenuList      = document.getElementById('infomenuList');
}

// ========================================
// AndroidBridge ヘルパー
// ========================================
const Bridge = {
    available() {
        return typeof AndroidBridge !== 'undefined' && AndroidBridge !== null;
    },
    isRunning()       { return this.available() ? AndroidBridge.isServiceRunning()   : false; },
    start()           { if (this.available()) AndroidBridge.startBackground(); },
    stop()            { if (this.available()) AndroidBridge.stopBackground(); },
    log(msg)          { if (this.available()) AndroidBridge.log(msg); },
    setTts(enabled)   { if (this.available() && AndroidBridge.setTtsEnabled)          AndroidBridge.setTtsEnabled(enabled); },
    setNotif(enabled) { if (this.available() && AndroidBridge.setNotificationEnabled) AndroidBridge.setNotificationEnabled(enabled); },
};

// ========================================
// Java → HTML コールバック
// ========================================
window.onEarthquakeData = function (jsonStr) {
    Bridge.log('[onEarthquakeData] ' + jsonStr.substring(0, 60));
    try {
        const data = JSON.parse(jsonStr);
        onP2PMessage(data);
    } catch (e) {
        console.error('onEarthquakeData parse error:', e);
    }
};

window.onConnectionStateChanged = function (connected, willReconnect) {
    Bridge.log('[onConnectionStateChanged] connected=' + connected + ' willReconnect=' + willReconnect);
    updateConnectionStatus(elements.p2pIndicator, elements.p2pText, connected, willReconnect);
};

window.onServiceStateChanged = function (running) {
    Bridge.log('[onServiceStateChanged] running=' + running);
    syncServiceButtons(running);
    if (!running) {
        updateConnectionStatus(elements.p2pIndicator, elements.p2pText, false, false);
    }
};

// ========================================
// 時計
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
    if (!elements.textarea) return;
    if (index < 0 || index >= AllWebsocketData.length) {
        elements.textarea.value = '(受信データがありません)';
        updatePagebarLabel(index);
        return;
    }

    let data = AllWebsocketData[index];
    if (typeof data === 'string') {
        try { data = JSON.parse(data); }
        catch (e) { elements.textarea.value = '(データの読み込みに失敗しました)'; return; }
    }

    elements.textarea.value = formatP2PMessage(data);
    currentIndex = index;
    updatePagebarLabel(index);
    updateNavButtons();
}

// ========================================
// P2Pメッセージのフォーマット
// ========================================
function formatP2PMessage(data) {
    const code = data.code;

    if (code === 551) return format551(data);
    if (code === 552) return format552(data);
    if (code === 554) return format554(data);
    if (code === 555) return format555(data);
    if (code === 556) return format556(data);
    if (code === 561) return format561(data);
    if (code === 9611) return format9611(data);

    return `【不明な情報】\nコード: ${code}\n${JSON.stringify(data, null, 2)}`;
}

// ── 551: 地震情報 ──
function format551(d) {
    const issue = d.issue ?? {};
    const quake = d.earthquake ?? {};
    const hypo  = quake.hypocenter ?? {};
    const points = d.points ?? [];
    const comments = d.comments ?? {};

    let s = '【地震情報】\n';
    s += `発表機関 : ${issue.source ?? '不明'}\n`;
    s += `発表日時 : ${issue.time ?? ''}\n`;
    s += `情報種別 : ${issueType(issue.type ?? '')}\n`;
    if (issue.correct && issue.correct !== 'None') s += `訂正情報 : ${correctType(issue.correct)}\n`;
    s += '\n';
    s += `発生日時 : ${quake.time ?? '不明'}\n`;
    s += `震　　源 : ${hypo.name ?? '不明'}\n`;
    if (hypo.magnitude >= 0) s += `マグニチュード : M${hypo.magnitude}\n`;
    if (hypo.depth >= 0)     s += `深　　さ : ${hypo.depth === 0 ? 'ごく浅い' : hypo.depth + 'km'}\n`;
    if (hypo.latitude > -100) s += `緯度/経度 : ${hypo.latitude} / ${hypo.longitude}\n`;
    s += `最大震度 : ${scaleToText(quake.maxScale)}\n`;
    s += `国内津波 : ${domesticTsunami(quake.domesticTsunami ?? 'None')}\n`;
    s += `海外津波 : ${foreignTsunami(quake.foreignTsunami ?? 'None')}\n`;

    if (points.length > 0) {
        s += '\n【各地の震度】\n';
        const max = Math.min(points.length, 30);
        for (let i = 0; i < max; i++) {
            const p = points[i];
            s += `  ${p.pref ?? ''} ${p.addr ?? ''} : 震度${scaleToText(p.scale)}\n`;
        }
        if (points.length > 30) s += `  …他 ${points.length - 30} 地点\n`;
    }

    const free = comments.freeFormComment ?? '';
    if (free) s += `\n${free}\n`;

    return s.trim();
}

// ── 552: 津波予報 ──
function format552(d) {
    const cancelled = d.cancelled ?? false;
    const issue = d.issue ?? {};
    let s = cancelled ? '【津波予報 解除】\n' : '【津波予報】\n';
    s += `発表機関 : ${issue.source ?? '不明'}\n`;
    s += `発表日時 : ${issue.time ?? ''}\n`;

    if (!cancelled) {
        const areas = d.areas ?? [];
        for (const a of areas) {
            s += `\n─ ${a.name ?? ''} ─\n`;
            s += `  種　別 : ${tsunamiGrade(a.grade ?? '')}\n`;
            s += `  直ちに来襲 : ${a.immediate ? 'はい' : 'いいえ'}\n`;
            const fh = a.firstHeight ?? {};
            if (fh.condition) s += `  到達状況 : ${fh.condition}\n`;
            if (fh.arrivalTime) s += `  到達予想 : ${fh.arrivalTime}\n`;
            const mh = a.maxHeight ?? {};
            if (mh.description) s += `  予想高さ : ${mh.description}\n`;
        }
    }
    return s.trim();
}

// ── 554: EEW検出 ──
function format554(d) {
    return `【緊急地震速報 検出】\n検出時刻 : ${d.time ?? ''}\n種　別 : ${eewDetectionType(d.type ?? '')}`;
}

// ── 555: Areapeers ──
function format555(d) {
    const areas = d.areas ?? [];
    let total = 0;
    areas.forEach(a => { total += a.peer ?? 0; });
    return `【ピア情報】\n接続ピア総数 : ${total}\n地域数 : ${areas.length}`;
}

// ── 556: EEW警報 ──
function format556(d) {
    const cancelled = d.cancelled ?? false;
    const test      = d.test ?? false;
    const issue = d.issue ?? {};
    const quake = d.earthquake ?? {};
    const hypo  = quake.hypocenter ?? {};

    let s = test ? '【緊急地震速報（テスト）】\n' : '【緊急地震速報（警報）】\n';
    if (cancelled) { s += '⚠ この速報は取消されました\n'; return s.trim(); }

    s += `発表時刻 : ${issue.time ?? ''}\n`;
    s += `第 ${issue.serial ?? '?'} 報\n\n`;
    s += `発生時刻 : ${quake.originTime ?? '不明'}\n`;
    s += `震　　央 : ${hypo.name ?? '不明'}\n`;
    if (hypo.reduceName && hypo.reduceName !== hypo.name) s += `（${hypo.reduceName}）\n`;
    if (hypo.magnitude >= 0) s += `マグニチュード : 推定 M${hypo.magnitude}\n`;
    if (hypo.depth >= 0) s += `深　　さ : ${Math.round(hypo.depth) === 0 ? 'ごく浅い' : Math.round(hypo.depth) + 'km'}\n`;
    if (quake.condition) s += `備　　考 : ${quake.condition}\n`;

    const areas = d.areas ?? [];
    if (areas.length > 0) {
        s += '\n【警報対象地域】\n';
        for (const a of areas) {
            s += `  ${a.pref ?? ''} ${a.name ?? ''}\n`;
            s += `  予測震度 : ${eewScaleRange(a.scaleFrom, a.scaleTo)}\n`;
            if (a.arrivalTime && a.arrivalTime !== 'null') s += `  主要動到達 : ${a.arrivalTime}\n`;
        }
    }
    return s.trim();
}

// ── 561: 地震感知情報 ──
function format561(d) {
    const area = d.area ?? -1;
    const areaName = area >= 0 ? (typeof EpspArea !== 'undefined' ? EpspArea.nameOf(area) : `コード${area}`) : '不明';
    return `【地震感知情報】\n受信日時 : ${d.time ?? ''}\n地　　域 : ${areaName} (コード: ${area})`;
}

// ── 9611: 感知解析 ──
function format9611(d) {
    const conf = d.confidence ?? 0;
    let s = '【地震感知情報 解析結果】\n';
    s += `評価日時 : ${d.time ?? ''}\n`;
    s += `開始日時 : ${d.started_at ?? ''}\n`;
    s += `件　　数 : ${d.count ?? 0}\n`;
    s += `信頼度 : ${conf.toFixed(5)} (${confidenceLabel(conf)})\n`;

    const areaConf = d.area_confidences ?? {};
    const keys = Object.keys(areaConf);
    if (keys.length > 0) {
        s += '\n【地域別信頼度】\n';
        for (const key of keys) {
            const ac = areaConf[key] ?? {};
            const code = parseFloat(key);
            const areaName = (typeof EpspArea !== 'undefined') ? EpspArea.nameOf(Math.round(code)) : `コード${key}`;
            s += `  ${areaName} : ${ac.display ?? '-'} (${ac.count ?? 0}件)\n`;
        }
    }
    return s.trim();
}

// ========================================
// ページバー更新
// ========================================
function updatePagebar() {
    if (!elements.pagebar) return;
    const maxIndex = Math.max(0, AllWebsocketData.length - 1);
    elements.pagebar.max = maxIndex;

    // 最新データを見ている場合は追従、さかのぼり中は固定
    const isAtLatest = (currentIndex < 0 || currentIndex === parseInt(elements.pagebar.max) - 1);
    if (isAtLatest) {
        elements.pagebar.value = maxIndex;
    }
    updateDataCountBadge();
}

function updatePagebarLabel(index) {
    if (!elements.pagebarLabel) return;
    if (index < 0 || index >= AllWebsocketData.length) {
        elements.pagebarLabel.textContent = '0 / 0';
        return;
    }
    elements.pagebarLabel.textContent = `${index + 1} / ${AllWebsocketData.length}`;
}

function updateDataCountBadge() {
    if (!elements.dataCountBadge) return;
    elements.dataCountBadge.textContent = AllWebsocketData.length;
}

function updateNavButtons() {
    if (elements.prevBtn)   elements.prevBtn.disabled   = (currentIndex <= 0);
    if (elements.nextBtn)   elements.nextBtn.disabled   = (currentIndex >= AllWebsocketData.length - 1);
    if (elements.latestBtn) elements.latestBtn.disabled = (currentIndex >= AllWebsocketData.length - 1);
}

// ========================================
// WebSocketメッセージ受信
// ========================================
function onP2PMessage(msg) {
    AllWebsocketData.push(msg);
    updatePagebar();
    if (typeof P2PMap === 'function') P2PMap(msg);

    // 最新を常に表示（さかのぼり中は追従しない）
    const isAtLatest = currentIndex < 0 || currentIndex === AllWebsocketData.length - 2;
    if (isAtLatest) {
        currentIndex = AllWebsocketData.length - 1;
        elements.pagebar.value = currentIndex;
        updateMainTextarea(currentIndex);
    } else {
        updateDataCountBadge();
        updateNavButtons();
    }

    updateKoishiSpeech(msg);
    addInfomenuEntry(msg);
}

// ========================================
// 情報メニューに項目を追加
// ========================================
function addInfomenuEntry(data) {
    if (!elements.infomenuList) return;
    const code = data.code;

    // 555(ピア情報)は追加しない
    if (code === 555) return;

    const item = document.createElement('div');
    item.className = 'info-entry';
    item.setAttribute('data-code', code);

    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`;

    const labelMap = {
        551: '🟡 地震情報',
        552: '🔵 津波予報',
        554: '🔴 緊急地震速報(検出)',
        556: '🔴 緊急地震速報(警報)',
        561: '🟢 地震感知',
        9611: '🟢 感知解析',
    };
    const label = labelMap[code] ?? `📡 コード${code}`;

    let detail = '';
    if (code === 551) {
        const q = data.earthquake ?? {};
        const h = q.hypocenter ?? {};
        detail = `${h.name ?? '不明'} M${h.magnitude ?? '?'} 最大震度${scaleToText(q.maxScale)}`;
    } else if (code === 556) {
        const h = data.earthquake?.hypocenter ?? {};
        detail = `${h.name ?? '不明'} 推定M${h.magnitude ?? '?'}`;
    } else if (code === 552) {
        detail = data.cancelled ? '解除' : '発表';
    }

    item.innerHTML = `
        <span class="info-entry-label">${label}</span>
        <span class="info-entry-time">${timeStr}</span>
        ${detail ? `<span class="info-entry-detail">${detail}</span>` : ''}
    `;

    const idx = AllWebsocketData.length - 1;
    item.addEventListener('click', () => {
        currentIndex = idx;
        if (elements.pagebar) elements.pagebar.value = idx;
        updateMainTextarea(idx);
        // 情報メニューを閉じる
        if (elements.infomenu) elements.infomenu.classList.remove('open');
    });

    // 先頭に追加（新しいものが上に来る）
    elements.infomenuList.insertBefore(item, elements.infomenuList.firstChild);
}

// ========================================
// こいしの吹き出し更新
// ========================================
function updateKoishiSpeech(data) {
    if (!elements.koishiText && !elements.koisiarrow) return;
    const target = elements.koishiText || elements.koisiarrow;
    const code = data.code;

    let msg = '';
    if (code === 556) {
        const hypo = data.earthquake?.hypocenter;
        msg = `⚡ 緊急地震速報！${hypo?.name ?? ''}`;
    } else if (code === 551) {
        const maxScale = data.earthquake?.maxScale;
        msg = `地震情報 最大震度${scaleToText(maxScale)}`;
    } else if (code === 552) {
        msg = data.cancelled ? '津波予報が解除されました' : '⚠️ 津波予報が発表されました';
    } else if (code === 554) {
        msg = '⚡ 緊急地震速報を検出しました';
    } else if (code === 561) {
        msg = '地震感知情報を受信しました';
    } else if (code === 9611) {
        msg = '地震感知 解析結果を受信';
    } else {
        return;
    }

    if (msg) {
        target.textContent = msg;
        clearTimeout(target._speechTimer);
        target._speechTimer = setTimeout(() => {
            target.textContent = '古明地こいしだよ!!';
        }, 5000);
    }
}

// ========================================
// 震度コード変換（P2PConvertsと同等）
// ========================================
function scaleToText(scale) {
    const map = { 10:'1', 20:'2', 30:'3', 40:'4', 45:'5弱', 46:'5弱以上(推定)', 50:'5強', 55:'6弱', 60:'6強', 70:'7' };
    return map[scale] ?? '不明';
}
function issueType(t) {
    const m = { ScalePrompt:'震度速報', Destination:'震源に関する情報', ScaleAndDestination:'震度・震源に関する情報', DetailScale:'各地の震度に関する情報', Foreign:'遠地地震に関する情報', Other:'その他' };
    return m[t] ?? t;
}
function correctType(c) {
    const m = { None:'訂正なし', Unknown:'不明', ScaleOnly:'震度のみ訂正', DestinationOnly:'震源のみ訂正', ScaleAndDestination:'震度・震源を訂正' };
    return m[c] ?? c;
}
function domesticTsunami(t) {
    const m = { None:'なし', Unknown:'不明', Checking:'調査中', NonEffective:'若干の海面変動（被害の心配なし）', Watch:'津波注意報', Warning:'津波予報（種類不明）' };
    return m[t] ?? t;
}
function foreignTsunami(t) {
    const m = { None:'なし', Unknown:'不明', Checking:'調査中', NonEffectiveNearby:'震源近傍で小さな津波の可能性（被害なし）', WarningNearby:'震源近傍で津波の可能性', WarningPacific:'太平洋で津波の可能性', WarningPacificWide:'太平洋の広域で津波の可能性', WarningIndian:'インド洋で津波の可能性', WarningIndianWide:'インド洋の広域で津波の可能性', Potential:'この規模では津波の可能性あり' };
    return m[t] ?? t;
}
function tsunamiGrade(g) {
    const m = { MajorWarning:'大津波警報', Warning:'津波警報', Watch:'津波注意報', Unknown:'不明' };
    return m[g] ?? g;
}
function eewDetectionType(t) {
    const m = { Full:'チャイム＋音声', Chime:'チャイムのみ' };
    return m[t] ?? t;
}
function eewScaleRange(from, to) {
    if (from === to) return '震度' + scaleToText(from);
    if (to === 99)   return '震度' + scaleToText(from) + '以上';
    return '震度' + scaleToText(from) + '〜' + scaleToText(to);
}
function confidenceLabel(c) {
    if (c <= 0)       return '非表示';
    if (c >= 0.98052) return 'レベル4';
    if (c >= 0.97024) return 'レベル3';
    if (c >= 0.97015) return 'レベル1';
    if (c >= 0.96774) return 'レベル2';
    return 'レベル不明';
}

// ========================================
// 接続状態表示更新
// ========================================
function updateConnectionStatus(indicator, textElement, isConnected, willReconnect = false) {
    if (!indicator || !textElement) return;
    indicator.classList.toggle('status-connected',    isConnected);
    indicator.classList.toggle('status-disconnected', !isConnected);
    indicator.classList.toggle('status-reconnecting', !isConnected && willReconnect);

    if (isConnected)           textElement.textContent = '接続中';
    else if (willReconnect)    textElement.textContent = '再接続中…';
    else                       textElement.textContent = '切断';
}

// ========================================
// Service ボタン同期
// ========================================
function syncServiceButtons(running) {
    if (elements.startBtn) {
        elements.startBtn.disabled = running;
        elements.startBtn.setAttribute('aria-pressed', String(!running));
    }
    if (elements.stopBtn) {
        elements.stopBtn.disabled = !running;
        elements.stopBtn.setAttribute('aria-pressed', String(running));
    }
}

// ========================================
// TTS / 通知トグル
// ========================================
function setupToggle(toggleEl, getter, setter) {
    if (!toggleEl) return;
    toggleEl.checked = getter();
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
        pos => {
            const { latitude, longitude } = pos.coords;
            fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}`)
                .then(r => r.json())
                .then(data => {
                    const place = data.address.city || data.address.town || data.address.village || '不明な地域';
                    userLocation = { lat: latitude, lon: longitude, place };
                    if (elements.nowLocate) elements.nowLocate.textContent = `${place} (${latitude.toFixed(1)}, ${longitude.toFixed(1)})`;
                    if (typeof addUserLocationMarker === 'function') addUserLocationMarker();
                })
                .catch(() => { if (elements.nowLocate) elements.nowLocate.textContent = '位置情報の取得に失敗'; });
        },
        err => {
            const msgs = {
                [err.PERMISSION_DENIED]:    '位置情報の許可が必要です',
                [err.POSITION_UNAVAILABLE]: '位置情報が利用できません',
                [err.TIMEOUT]:              '位置情報の取得がタイムアウト',
            };
            if (elements.nowLocate) elements.nowLocate.textContent = msgs[err.code] ?? '不明なエラー';
        }
    );
}

// ========================================
// デモ用テストデータ注入（Android外の確認用）
// ========================================
function injectDemoData() {
    const demos = [
        { code: 555, areas: [{ peer: 120 }, { peer: 85 }, { peer: 60 }] },
        { code: 561, area: 130, time: '2025/06/01 09:12:34' },
        {
            code: 551,
            issue: { source: '気象庁', time: '2025/06/01 09:15:00', type: 'ScaleAndDestination', correct: 'None' },
            earthquake: {
                time: '2025/06/01 09:14:52',
                hypocenter: { name: '茨城県沖', magnitude: 5.1, depth: 60, latitude: 36.1, longitude: 140.7 },
                maxScale: 40, domesticTsunami: 'None', foreignTsunami: 'None'
            },
            points: [
                { pref: '茨城県', addr: '水戸市', scale: 40 },
                { pref: '茨城県', addr: 'ひたちなか市', scale: 40 },
                { pref: '千葉県', addr: '柏市', scale: 30 },
                { pref: '東京都', addr: '足立区', scale: 30 },
                { pref: '埼玉県', addr: 'さいたま市', scale: 20 },
            ],
            comments: { freeFormComment: '' }
        },
        {
            code: 556,
            cancelled: false, test: false,
            issue: { time: '2025/06/01 09:13:45', serial: '2' },
            earthquake: {
                originTime: '2025/06/01 09:13:40',
                hypocenter: { name: '茨城県南部', reduceName: '茨城南部', magnitude: 5.3, depth: 55 },
                condition: ''
            },
            areas: [
                { pref: '茨城県', name: '水戸', scaleFrom: 40, scaleTo: 40, arrivalTime: '09:14:10' },
                { pref: '千葉県', name: '柏', scaleFrom: 30, scaleTo: 40, arrivalTime: '09:14:25' },
            ]
        },
    ];

    let delay = 500;
    demos.forEach(d => {
        setTimeout(() => onP2PMessage(d), delay);
        delay += 800;
    });
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

    // ページバー（スライダー）
    if (elements.pagebar) {
        elements.pagebar.addEventListener('input', e => {
            const idx = parseInt(e.target.value, 10);
            currentIndex = idx;
            updateMainTextarea(idx);
        });
    }

    // ナビボタン（前へ / 次へ / 最新へ）
    if (elements.prevBtn) {
        elements.prevBtn.addEventListener('click', () => {
            if (currentIndex > 0) {
                currentIndex--;
                elements.pagebar.value = currentIndex;
                updateMainTextarea(currentIndex);
            }
        });
    }
    if (elements.nextBtn) {
        elements.nextBtn.addEventListener('click', () => {
            if (currentIndex < AllWebsocketData.length - 1) {
                currentIndex++;
                elements.pagebar.value = currentIndex;
                updateMainTextarea(currentIndex);
            }
        });
    }
    if (elements.latestBtn) {
        elements.latestBtn.addEventListener('click', () => {
            if (AllWebsocketData.length > 0) {
                currentIndex = AllWebsocketData.length - 1;
                elements.pagebar.value = currentIndex;
                updateMainTextarea(currentIndex);
            }
        });
    }

    // 地図切替
    if (elements.mapChangeBtn) {
        elements.mapChangeBtn.addEventListener('click', toggleMapMode);
    }

    // AndroidBridge 連携
    if (Bridge.available()) {
        const running = Bridge.isRunning();
        syncServiceButtons(running);
        updateConnectionStatus(elements.p2pIndicator, elements.p2pText, running);

        if (elements.startBtn) {
            elements.startBtn.addEventListener('click', () => {
                Bridge.start();
                elements.startBtn.disabled = true;
            });
        }
        if (elements.stopBtn) {
            elements.stopBtn.addEventListener('click', () => {
                Bridge.stop();
                elements.stopBtn.disabled = true;
            });
        }

        setupToggle(
            elements.ttsToggle,
            () => AndroidBridge.isTtsEnabled ? AndroidBridge.isTtsEnabled() : true,
            v  => Bridge.setTts(v)
        );
        setupToggle(
            elements.notifToggle,
            () => AndroidBridge.isNotificationEnabled ? AndroidBridge.isNotificationEnabled() : true,
            v  => Bridge.setNotif(v)
        );

    } else {
        console.warn('AndroidBridge is not available — running in browser mode');
        if (elements.p2pText) elements.p2pText.textContent = 'ブラウザモード';
        if (elements.startBtn) elements.startBtn.disabled = false;
        if (elements.stopBtn)  elements.stopBtn.disabled  = true;

        // ブラウザ確認用にデモデータを注入
        injectDemoData();
    }

    // 初期表示
    updateMainTextarea(-1);
    getUserLocation();
}

// ページ読み込み時に実行
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

window.addEventListener('beforeunload', () => {
    if (timeUpdateInterval) clearInterval(timeUpdateInterval);
});

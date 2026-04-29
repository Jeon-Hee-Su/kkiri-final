// [1] 전역 변수
let stream = null;
let currentFileName = "";
let pendingAction = null;
let selectedGroupId = null;
let linkedTransactionId = null;
let linkedTxAmount      = 0;

// =====================================================================
// [초기화] 페이지 로드 시 모드 판별
// =====================================================================
document.addEventListener('DOMContentLoaded', () => {
    // Thymeleaf 주입값 우선, 없으면 URL 파라미터에서 직접 읽기 (fallback)
    const urlParams = new URLSearchParams(window.location.search);

    const txAmount = Number(urlParams.get('txAmount') || 0);

    const txId  = (typeof SERVER_TRANSACTION_ID !== 'undefined' && Number(SERVER_TRANSACTION_ID) > 0)
        ? Number(SERVER_TRANSACTION_ID)
        : Number(urlParams.get('transactionId') || 0);

    const grpId = (typeof SERVER_LINKED_GROUP_ID !== 'undefined' && Number(SERVER_LINKED_GROUP_ID) > 0)
        ? Number(SERVER_LINKED_GROUP_ID)
        : Number(urlParams.get('groupId') || 0);

    if (txId && txId !== 0) {
        linkedTransactionId = txId;
        selectedGroupId     = grpId;
        linkedTxAmount      = txAmount;
        const wrap = document.getElementById('group-selector-wrap');
        if (wrap) wrap.classList.add('hidden');
        const banner = document.getElementById('transaction-link-banner');
        if (banner) banner.classList.remove('hidden');
    } else {
        linkedTransactionId = null;
        loadMyGroups();
    }

    // tab=history로 직접 진입한 경우: groupId를 URL에서 읽어 바로 fetchHistory 호출
    if (urlParams.get('tab') === 'history' && grpId) {
        selectedGroupId = grpId;
        fetchHistory();
    }

    const uploadInput = document.getElementById('receipt-upload');
    if (uploadInput) {
        uploadInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = (ev) => processImageFlow(ev.target.result);
                reader.readAsDataURL(file);
            }
        });
    }
});

// =====================================================================
// [일반 모드] 그룹 목록 로드
// =====================================================================
async function loadMyGroups() {
    try {
        const res = await authFetch('/api/group/my-groups');
        if (!res.ok) throw new Error();
        const groups = await res.json();

        const selector = document.getElementById('group-selector');
        if (!selector) return;

        if (!groups || groups.length === 0) {
            selector.innerHTML = '<option value="">소속 그룹 없음</option>';
            return;
        }

        selector.innerHTML = groups.map(g =>
            `<option value="${g.groupId}">${g.groupName}</option>`
        ).join('');

        selectedGroupId = groups[0].groupId;
        selector.value = selectedGroupId;

        selector.addEventListener('change', () => {
            selectedGroupId = parseInt(selector.value);
        });

    } catch (e) {
        console.error('그룹 목록 로드 실패:', e);
    }
}

function openScanNotice(mode) {
    pendingAction = mode;
    document.getElementById('scan-notice-modal').classList.remove('hidden');
}

function closeScanNotice() {
    document.getElementById('scan-notice-modal').classList.add('hidden');
}

function confirmScan() {
    const mode = pendingAction;
    closeScanNotice();
    if (mode === 'CAMERA') {
        actuallyStartScanner();
    } else if (mode === 'GALLERY') {
        document.getElementById('receipt-upload').click();
    }
}

// [2] 탭 전환
function switchTab(tab) {
    const editSection = document.getElementById('edit-section');
    const storeName = document.getElementById('store-name').value;

    if (tab === 'history' && !editSection.classList.contains('hidden')) {
        if (storeName || document.querySelectorAll('#items-container > div').length > 0) {
            if (!confirm("작성 중인 데이터가 저장되지 않고 사라집니다. 이동하시겠습니까?")) return;
        }
        resetEditForm();
    }

    const uploadSection  = document.getElementById('upload-section');
    const historySection = document.getElementById('history-section');
    const tabUpload  = document.getElementById('tab-upload');
    const tabHistory = document.getElementById('tab-history');

    if (tab === 'upload') {
        uploadSection.classList.remove('hidden');
        historySection.classList.add('hidden');
        tabUpload.classList.add('bg-white', 'dark:bg-slate-700', 'shadow-sm', 'text-emerald-600', 'font-black');
        tabUpload.classList.remove('text-slate-500', 'font-bold');
        tabHistory.classList.remove('bg-white', 'dark:bg-slate-700', 'shadow-sm', 'text-emerald-600', 'font-black');
        tabHistory.classList.add('text-slate-500', 'font-bold');
    } else {
        uploadSection.classList.add('hidden');
        historySection.classList.remove('hidden');
        tabHistory.classList.add('bg-white', 'dark:bg-slate-700', 'shadow-sm', 'text-emerald-600', 'font-black');
        tabHistory.classList.remove('text-slate-500', 'font-bold');
        tabUpload.classList.remove('bg-white', 'dark:bg-slate-700', 'shadow-sm', 'text-emerald-600', 'font-black');
        tabUpload.classList.add('text-slate-500', 'font-bold');
        fetchHistory();
    }
}

function resetEditForm() {
    document.getElementById('edit-section').classList.add('hidden');
    document.getElementById('upload-section').classList.remove('hidden');
    document.getElementById('store-name').value = '';
    document.getElementById('total-amount').value = 0;
    document.getElementById('category').value = '식비';
    document.getElementById('items-container').innerHTML = '';
    currentFileName = "";
}

// [3] 수동 입력
function startManualEntry() {
    document.getElementById('upload-section').classList.add('hidden');
    document.getElementById('edit-section').classList.remove('hidden');
    document.getElementById('store-name').value = '';
    document.getElementById('pay-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('total-amount').value = 0;
    document.getElementById('category').value = '식비';
    document.getElementById('items-container').innerHTML = '';
    addItemRow();
    currentFileName = "";
}

// [4] OCR 분석
async function processImageFlow(imageData) {
    closeScanNotice();
    document.getElementById('image-preview').src = imageData;
    document.getElementById('upload-section').classList.add('hidden');
    document.getElementById('scanning-section').classList.remove('hidden');

    try {
        const blob = await (await authFetch(imageData)).blob();
        const formData = new FormData();
        formData.append("file", blob, "receipt.jpg");

        const response = await authFetch('/api/ocr/analyze', { method: 'POST', body: formData });
        if (!response.ok) throw new Error("분석 실패");

        const result = await response.json();
        currentFileName = result.fileName;
        const parsedOcr = JSON.parse(result.ocrData);
        displayOcrResult(parsedOcr);

    } catch (error) {
        alert("영수증 분석 중 오류가 발생했습니다.");
        switchTab('upload');
    }
}

function displayOcrResult(data) {
    document.getElementById('scanning-section').classList.add('hidden');
    document.getElementById('edit-section').classList.remove('hidden');
    document.getElementById('store-name').value = data.store || '';
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('pay-date').value = data.date || today;
    document.getElementById('total-amount').value = data.total || 0;

    const container = document.getElementById('items-container');
    container.innerHTML = '';
    if (data.items && data.items.length > 0) {
        data.items.forEach(item => {
            addItemRow(item.itemName || item.name || '', item.price || 0, item.quantity || 1);
        });
    }
    calculateTotal();
    setTimeout(calculateTotal, 200);
}

// [5] 품목 행 추가
function addItemRow(name = '', price = '', quantity = 1) {
    const container = document.getElementById('items-container');
    const div = document.createElement('div');
    div.className = "flex gap-2 items-center animate-in fade-in slide-in-from-left-2 duration-300";
    div.innerHTML = `
        <input type="text" placeholder="품목명" class="flex-1 h-10 rounded-lg border-none bg-slate-50 dark:bg-slate-800 px-3 text-xs font-bold focus:ring-1 focus:ring-emerald-500 item-name" />
        <input type="number" placeholder="단가" class="w-20 h-10 rounded-lg border-none bg-slate-50 dark:bg-slate-800 px-3 text-xs font-bold focus:ring-1 focus:ring-emerald-500 text-right item-price" oninput="calculateTotal()" />
        <div class="flex items-center bg-slate-100 dark:bg-slate-800 rounded-lg h-10 px-2 gap-1">
            <span class="text-[10px] font-bold text-slate-400">x</span>
            <input type="number" placeholder="수량" class="w-10 bg-transparent border-none p-0 text-center text-xs font-black focus:ring-0 item-quantity" oninput="calculateTotal()" />
        </div>
        <button onclick="this.parentElement.remove(); calculateTotal();" class="text-slate-300 hover:text-red-500 transition-colors">
            <span class="material-symbols-outlined text-lg">cancel</span>
        </button>
    `;
    div.querySelector('.item-name').value = name;
    div.querySelector('.item-price').value = price;
    div.querySelector('.item-quantity').value = quantity;
    container.appendChild(div);
}

function calculateTotal() {
    const rows = document.querySelectorAll('#items-container > div');
    let itemSum = 0;
    rows.forEach(row => {
        const price    = parseInt(row.querySelector('.item-price').value) || 0;
        const quantity = parseInt(row.querySelector('.item-quantity').value) || 0;
        itemSum += (price * quantity);
    });
    const totalInput = document.getElementById('total-amount');
    if (totalInput) totalInput.value = itemSum;
}

// [6] 카메라
async function actuallyStartScanner() {
    try {
        stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: "environment", width: { ideal: 1920 }, height: { ideal: 1080 } }
        });
        const video = document.getElementById('video');
        video.srcObject = stream;
        video.onloadedmetadata = () => video.play();
        document.getElementById('camera-modal').classList.remove('hidden');
    } catch (err) {
        alert("카메라를 켤 수 없습니다.");
    }
}

function startScanner()   { openScanNotice('CAMERA'); }
function triggerGallery() { openScanNotice('GALLERY'); }

function takeSnapshot() {
    const video  = document.getElementById('video');
    const canvas = document.getElementById('canvas');
    canvas.width  = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    closeScanner();
    processImageFlow(canvas.toDataURL('image/jpeg'));
}

function closeScanner() {
    if (stream) stream.getTracks().forEach(t => t.stop());
    document.getElementById('camera-modal').classList.add('hidden');
}

// =====================================================================
// [7] 저장: transactionId 있으면 거래에 연결, 없으면 일반 그룹 저장
// =====================================================================
async function submitToBackend() {
    const btn         = document.querySelector('button[onclick="submitToBackend()"]');
    const finalAmount = parseInt(document.getElementById('total-amount').value) || 0;
    const storeName   = document.getElementById('store-name').value;

    if (!storeName)       { alert("상호명을 입력해주세요."); return; }
    if (finalAmount <= 0) { alert("결제 금액을 확인해주세요."); return; }
    if (!selectedGroupId) { alert("저장할 그룹을 선택해주세요."); return; }

    // 거래 연동 모드: 실제 거래 금액과 입력한 금액 일치 검증
    if (linkedTxAmount > 0 && finalAmount !== linkedTxAmount) {
        alert(`거래 금액(${linkedTxAmount.toLocaleString()}원)과 입력한 결제금액(${finalAmount.toLocaleString()}원)이 맞지 않습니다.\n결제금액을 ${linkedTxAmount.toLocaleString()}원으로 맞춰주세요.`);
        return;
    }

    // 품목 합계 vs 최종 결제금액 일치 검증
    const rows = document.querySelectorAll('#items-container > div');
    let itemSum = 0;
    rows.forEach(row => {
        const price    = parseInt(row.querySelector('.item-price').value) || 0;
        const quantity = parseInt(row.querySelector('.item-quantity').value) || 0;
        itemSum += (price * quantity);
    });
    if (rows.length > 0 && itemSum !== finalAmount) {
        alert(`품목 합계(${itemSum.toLocaleString()}원)가 최종 결제금액(${finalAmount.toLocaleString()}원)과 맞지 않습니다.\n품목을 확인해주세요.`);
        return;
    }

    const modeLabel = linkedTransactionId ? '거래에 영수증 연결' : '그룹 장부 저장';
    if (!confirm(`[${modeLabel}]\n상호명: ${storeName}\n총 금액: ${finalAmount.toLocaleString()}원\n저장할까요?`)) return;

    const expenseData = {
        groupId:       selectedGroupId,
        merchantName:  storeName,
        category:      document.getElementById('category').value,
        amount:        finalAmount,
        fileName:      currentFileName,
        transactionId: linkedTransactionId || null,   // ← 핵심
        items:         []
    };

    document.querySelectorAll('#items-container > div').forEach(row => {
        const itemName = row.querySelector('input[type="text"]').value;
        const price    = parseInt(row.querySelector('.item-price').value) || 0;
        const quantity = parseInt(row.querySelector('.item-quantity').value) || 0;
        if (itemName) expenseData.items.push({ itemName, price, quantity });
    });

    try {
        btn.disabled = true;
        btn.innerHTML = '저장 중...';
        const response = await authFetch('/api/receipts/save', {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(expenseData)
        });
        if (response.ok) {
            alert("성공적으로 저장되었습니다!");
            window.location.href = `/group/detail/${selectedGroupId}`;
        } else {
            const errText = await response.text();
            throw new Error(errText || "저장 실패");
        }
    } catch (error) {
        alert(error.message);
        btn.disabled  = false;
        btn.innerHTML = "그룹 장부에 저장하기";
    }
}

// =====================================================================
// [8] 과거 내역 조회
// =====================================================================
async function fetchHistory() {
    const historyContainer = document.getElementById('history-list-container');
    if (!historyContainer) return;

    if (!selectedGroupId) {
        historyContainer.innerHTML = '<p class="text-center py-10 text-slate-400">그룹을 먼저 선택해주세요.</p>';
        return;
    }

    try {
        const response = await authFetch(`/api/receipts/list?groupId=${selectedGroupId}`);
        if (!response.ok) throw new Error("데이터를 가져오지 못했습니다.");
        const list = await response.json();

        if (list.length === 0) {
            historyContainer.innerHTML = '<p class="text-center py-10 text-slate-400">내역이 없습니다.</p>';
            return;
        }

        historyContainer.innerHTML = list.map(item => `
            <div onclick="openItemModal(${item.expenseId}, '${item.merchantName}', ${item.amount})"
                class="bg-white dark:bg-slate-800 p-4 rounded-2xl shadow-sm flex items-center gap-4 mb-3 border border-slate-100 dark:border-slate-700 cursor-pointer">
                <div class="w-12 h-12 bg-emerald-50 dark:bg-emerald-900/30 rounded-full flex items-center justify-center text-emerald-600">
                    <span class="material-symbols-outlined">receipt_long</span>
                </div>
                <div class="flex-1">
                    <p class="font-bold text-sm text-slate-800 dark:text-slate-200">${item.merchantName}</p>
                    <p class="text-[10px] text-slate-400">${item.payDate} · ${item.category || ''}</p>
                </div>
                <div class="text-right">
                    <p class="font-black text-sm text-slate-900 dark:text-white">${item.amount.toLocaleString()}원</p>
                </div>
            </div>
        `).join('');

    } catch (error) {
        console.error("내역 로드 실패:", error);
    }
}

// =====================================================================
// [9] 품목 상세 모달
// =====================================================================
async function openItemModal(expenseId, merchantName, totalAmount) {
    document.getElementById('modal-title').textContent = merchantName;
    document.getElementById('modal-total').textContent = totalAmount.toLocaleString() + '원';
    document.getElementById('modal-items').innerHTML =
        '<p class="text-center text-slate-400 text-sm py-4 animate-pulse">불러오는 중...</p>';

    document.getElementById('item-detail-modal').classList.remove('hidden');

    try {
        const res   = await authFetch(`/api/receipts/items?expenseId=${expenseId}`);
        const items = await res.json();

        if (!items || items.length === 0) {
            document.getElementById('modal-items').innerHTML =
                '<p class="text-center text-slate-400 text-sm py-4">등록된 품목이 없습니다.</p>';
            return;
        }

        document.getElementById('modal-items').innerHTML = items.map(item => `
            <div class="flex justify-between items-center py-3 border-b border-slate-100 dark:border-slate-700 last:border-0">
                <div>
                    <p class="font-bold text-sm text-slate-800 dark:text-slate-200">${item.itemName}</p>
                    <p class="text-[11px] text-slate-400">${item.price.toLocaleString()}원 × ${item.quantity}개</p>
                </div>
                <p class="font-black text-sm text-emerald-600">${(item.price * item.quantity).toLocaleString()}원</p>
            </div>
        `).join('');

    } catch (e) {
        document.getElementById('modal-items').innerHTML =
            '<p class="text-center text-red-400 text-sm py-4">품목을 불러오지 못했습니다.</p>';
    }
}

function closeItemModal() {
    document.getElementById('item-detail-modal').classList.add('hidden');
}
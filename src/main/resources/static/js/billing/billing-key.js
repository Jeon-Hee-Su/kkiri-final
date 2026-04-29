// =====================================================================
// billing-key.js  —  PortOne V2 빌링키 등록/해제
// paymentmethod.html에서 사용
//
// V2 SDK 스크립트 필요 (paymentmethod.html에 추가):
// <script src="https://cdn.portone.io/v2/browser-sdk.js"></script>
// =====================================================================

// PortOne V2 식별값 (application.properties와 일치시킬 것)
const STORE_ID   = 'INIpayTest';        // ← 콘솔 > 내 식별코드 > Store ID
const CHANNEL_KEY = 'channel-key-7daed793-3b1d-4abf-9cad-4db5783dded6'; // ← 콘솔 > 채널 관리 > channelKey

// =====================================================================
// 페이지 로드 시 빌링키 등록 상태 조회
// =====================================================================
async function loadBillingKeyStatus() {
    try {
        const res = await authFetch('/api/billing/status', { credentials: 'include' });
        if (!res.ok) return;
        const data = await res.json();
        updateBillingUI(data.hasBillingKey);
    } catch (e) {
        console.error('빌링키 상태 조회 실패:', e);
        document.getElementById('billing-status-text').textContent = '상태 조회 실패';
    }
}

function updateBillingUI(hasBillingKey) {
    const statusText  = document.getElementById('billing-status-text');
    const registerBtn = document.getElementById('billing-register-btn');
    const cancelBtn   = document.getElementById('billing-cancel-btn');
    const checkIcon   = document.getElementById('billing-check');
    const billingIcon = document.getElementById('billing-icon');

    if (hasBillingKey) {
        statusText.textContent = '자동이체 카드 등록됨 · 납부일에 자동 이체';
        statusText.classList.replace('text-slate-500', 'text-emerald-600');
        registerBtn.classList.add('hidden');
        cancelBtn.classList.remove('hidden');
        checkIcon.classList.remove('hidden');
        billingIcon.className = 'w-12 h-12 rounded-xl bg-emerald-50 dark:bg-emerald-900/30 flex items-center justify-center text-emerald-600';
    } else {
        statusText.textContent = '미등록 · 카드를 등록하면 납부일에 자동이체';
        statusText.classList.replace('text-emerald-600', 'text-slate-500');
        registerBtn.classList.remove('hidden');
        cancelBtn.classList.add('hidden');
        checkIcon.classList.add('hidden');
        billingIcon.className = 'w-12 h-12 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400';
    }
}

// =====================================================================
// 빌링키 등록 — PortOne V2 SDK 호출
// =====================================================================
async function registerBillingKey() {
    if (typeof PortOne === 'undefined') {
        alert('결제 모듈을 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
        return;
    }

    try {
        // V2: PortOne.requestBillingKeyIssue()
        const response = await PortOne.requestBillingKeyIssue({
            storeId:    STORE_ID,
            channelKey: CHANNEL_KEY,
            billingKeyMethod: 'CARD',    // 카드 빌링키
            issueId:   'BILLING_' + Date.now(),  // 고유 발급 ID
            issueName: '자동이체 카드 등록',
            // 테스트 시: 카드번호 입력창에 테스트 카드번호 입력
            // (KG이니시스 테스트: 1234-1234-1234-1234)
        });

        if (response.code) {
            // 오류 응답
            alert('카드 등록 실패: ' + (response.message || response.code));
            return;
        }

        // V2: response.billingKey 로 전달됨
        await saveBillingKey(response.billingKey);

    } catch (e) {
        console.error('빌링키 발급 오류:', e);
        alert('카드 등록 중 오류가 발생했습니다.');
    }
}

// =====================================================================
// 서버에 billingKey 저장
// =====================================================================
async function saveBillingKey(billingKey) {
    try {
        const res = await authFetch('/api/billing/register', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ billingKey })   // V2: billingKey 필드
        });

        const data = await res.json();
        if (res.ok && data.success) {
            updateBillingUI(true);
            alert('✅ 자동이체 카드가 등록되었습니다!\n납부일에 회비가 자동으로 이체됩니다.');
        } else {
            alert('카드 등록 실패: ' + (data.message || '서버 오류'));
        }
    } catch (e) {
        console.error('빌링키 저장 실패:', e);
        alert('서버 통신 오류가 발생했습니다.');
    }
}

// =====================================================================
// 자동이체 해제
// =====================================================================
async function cancelBillingKey() {
    if (!confirm('자동이체를 해제하시겠습니까?\n납부일에 회비가 자동으로 이체되지 않습니다.')) return;

    try {
        const res = await authFetch('/api/billing/cancel', {
            method: 'POST',
            credentials: 'include'
        });
        const data = await res.json();
        if (res.ok && data.success) {
            updateBillingUI(false);
            alert('자동이체가 해제되었습니다.');
        } else {
            alert('해제 실패: ' + (data.message || '서버 오류'));
        }
    } catch (e) {
        console.error('빌링키 해제 실패:', e);
    }
}

// 페이지 로드 시 자동 실행
document.addEventListener('DOMContentLoaded', loadBillingKeyStatus);
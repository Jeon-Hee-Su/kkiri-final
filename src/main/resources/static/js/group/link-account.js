/**
 * link-account.js
 *
 * 흐름:
 * [계좌 등록] 은행선택 → 번호생성 → 등록버튼
 *   → 비밀번호 6자리 입력 → 재확인 → /api/account/register
 *   → 등록 완료 후 자동 계좌 선택
 *
 * [모임 참여] 계좌 선택 → 연결완료 버튼 → /api/group/join (비밀번호 확인 없이 바로)
 */

let selectedAccountNum = null;
let selectedAccountId  = null;  // ★ 빌링키 자동이체용 accountId
let currentGroupId     = null;

// 비밀번호 설정 상태
let setupPin  = '';   // 현재 입력 중
let firstPin  = '';   // 첫 번째 입력값 저장
let pendingBankCode      = '';
let pendingAccountNumber = '';

const inviteCode = document.getElementById('inviteCode').value;

document.addEventListener('DOMContentLoaded', async function () {
    await fetchGroupIdByCode();
    fetchMyAccounts();

    const form = document.getElementById('addAccountForm');
    if (form) form.addEventListener('submit', onAddAccountSubmit);
});

// =============================================
// [초기화] 초대코드 → groupId 조회
// =============================================
async function fetchGroupIdByCode() {
    try {
        const response = await fetch(`/api/groups/by-code/${inviteCode}`, { credentials: 'include' });
        if (response.ok) {
            const data = await response.json();
            currentGroupId = data.groupId;
        }
    } catch (e) {
        console.error('groupId 조회 실패:', e);
    }
}

// =============================================
// [조회] 전체 계좌 목록
// =============================================
async function fetchMyAccounts() {
    const container = document.getElementById('account-list-container');
    try {
        const response = await authFetch('/api/account/list', { credentials: 'include' });
        if (!response.ok) throw new Error('계좌 목록 조회 실패');
        const accounts = await response.json();
        renderAccounts(accounts);
    } catch (error) {
        console.error('계좌 조회 오류:', error);
        if (container) {
            container.innerHTML = `
                <div class="py-10 text-center text-red-400 text-sm">
                    <span class="material-symbols-outlined text-4xl mb-2 block">error_outline</span>
                    계좌 정보를 불러오지 못했습니다.
                </div>`;
        }
    }
}

// =============================================
// [렌더링] 계좌 카드 목록
// =============================================
function renderAccounts(accounts) {
    const container = document.getElementById('account-list-container');
    if (!container) return;

    if (!accounts || accounts.length === 0) {
        container.innerHTML = `
            <div class="py-12 text-center bg-slate-50 rounded-2xl border border-dashed border-slate-200">
                <span class="material-symbols-outlined text-5xl text-slate-200 mb-2 block">account_balance_wallet</span>
                <p class="text-slate-400 text-sm">등록된 계좌가 없습니다.<br>새 계좌를 먼저 등록해 주세요.</p>
            </div>`;
        return;
    }

    container.innerHTML = accounts.map(acc => {
        const isPrimary = acc.isDefault === 'Y';
        return `
        <button onclick="selectAccount(this, '${acc.accountNumber}', ${acc.accountId})"
                class="account-item w-full p-5 border-2 rounded-2xl flex items-center justify-between transition-all
                       ${isPrimary ? 'border-primary bg-primary/5' : 'border-slate-100 bg-white'}
                       hover:border-primary hover:bg-primary/5 shadow-sm mb-3">
            <div class="flex items-center gap-4">
                <div class="w-12 h-12 rounded-2xl ${isPrimary ? 'bg-primary' : 'bg-slate-800'} flex items-center justify-center text-white flex-shrink-0">
                    <span class="material-symbols-outlined text-xl">account_balance</span>
                </div>
                <div class="text-left">
                    <div class="flex items-center gap-2 mb-0.5">
                        <p class="font-bold text-slate-900 text-sm">${acc.bankName || '계좌'}</p>
                        ${isPrimary ? '<span class="px-1.5 py-0.5 bg-primary text-white text-[9px] font-bold rounded">주계좌</span>' : ''}
                    </div>
                    <p class="text-[11px] text-slate-400 font-mono">${acc.accountNumber}</p>
                    <p class="text-[11px] text-slate-500 font-bold mt-0.5">잔액 ${Number(acc.balance).toLocaleString()}원</p>
                </div>
            </div>
            <span class="material-symbols-outlined check-icon text-2xl ${isPrimary ? 'text-primary' : 'text-slate-200'}"
                  style="font-variation-settings:'FILL' 1;">check_circle</span>
        </button>`;
    }).join('');

    const primaryAcc = accounts.find(a => a.isDefault === 'Y');
    if (primaryAcc) {
        const firstBtn = container.querySelector('.account-item');
        if (firstBtn) selectAccount(firstBtn, primaryAcc.accountNumber);
    }
}

// =============================================
// [선택] 계좌 선택 토글
// =============================================
function selectAccount(element, accNum, accId) {
    document.querySelectorAll('.account-item').forEach(el => {
        el.classList.remove('border-primary', 'bg-primary/5');
        el.classList.add('border-slate-100', 'bg-white');
        const icon = el.querySelector('.check-icon');
        if (icon) { icon.classList.remove('text-primary'); icon.classList.add('text-slate-200'); }
    });
    element.classList.add('border-primary', 'bg-primary/5');
    element.classList.remove('border-slate-100', 'bg-white');
    const icon = element.querySelector('.check-icon');
    if (icon) { icon.classList.add('text-primary'); icon.classList.remove('text-slate-200'); }

    selectedAccountNum = accNum;
    selectedAccountId  = accId;   // ★ accountId 저장
    document.getElementById('submitBtn').disabled = false;
}

// =============================================
// [계좌 등록] 폼 submit → 비밀번호 모달 열기
// =============================================
function onAddAccountSubmit(e) {
    e.preventDefault();

    const bankCode      = document.getElementById('bankCode').value;
    const accountNumber = document.getElementById('accountNumberDisplay').innerText;

    if (!bankCode)                              { alert('은행을 선택해 주세요.'); return; }
    if (!accountNumber || accountNumber === '-') { alert('계좌번호를 먼저 생성해 주세요.'); return; }

    // 비밀번호 모달용 값 저장
    pendingBankCode      = bankCode;
    pendingAccountNumber = accountNumber;

    // 비밀번호 모달 열기
    openPasswordSetupModal();
}

// =============================================
// 비밀번호 설정 모달 제어
// =============================================
function openPasswordSetupModal() {
    resetPinState();
    document.getElementById('passwordSetupModal').classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function closePasswordSetupModal() {
    document.getElementById('passwordSetupModal').classList.add('hidden');
    document.body.style.overflow = 'auto';
    resetPinState();
}

function resetPinState() {
    setupPin = '';
    firstPin = '';
    updatePwDots();
    const statusEl = document.getElementById('pw-setup-status');
    if (statusEl) {
        statusEl.textContent = '숫자 6자리를 입력해주세요.';
        statusEl.className   = 'text-slate-500 text-base font-bold mb-14';
    }
}

function updatePwDots() {
    const dots = document.querySelectorAll('#passwordSetupModal .pw-dot');
    dots.forEach((dot, i) => {
        if (i < setupPin.length) {
            dot.classList.add('bg-primary');
            dot.classList.remove('border-2', 'border-slate-200');
        } else {
            dot.classList.remove('bg-primary');
            dot.classList.add('border-2', 'border-slate-200');
        }
    });
}

// =============================================
// 비밀번호 키패드 입력 (create-passbook과 동일 로직)
// =============================================
function handlePwInput(num) {
    if (setupPin.length >= 6) return;
    setupPin += num;
    updatePwDots();

    if (setupPin.length === 6) {
        if (firstPin === '') {
            // 1차 입력 완료 → 재확인 단계
            setTimeout(() => {
                firstPin = setupPin;
                setupPin = '';
                updatePwDots();
                const statusEl = document.getElementById('pw-setup-status');
                if (statusEl) {
                    statusEl.textContent = '확인을 위해 한 번 더 입력해주세요.';
                    statusEl.className   = 'text-primary text-base font-bold mb-14';
                }
            }, 300);
        } else {
            // 2차 입력 완료 → 대조
            setTimeout(async () => {
                if (firstPin === setupPin) {
                    // 일치 → 계좌 등록 API 호출
                    await handleAddAccount(pendingBankCode, pendingAccountNumber, firstPin);
                } else {
                    alert('비밀번호가 일치하지 않습니다. 다시 입력해주세요.');
                    resetPinState();
                }
            }, 300);
        }
    }
}

function handlePwDelete() {
    setupPin = setupPin.slice(0, -1);
    updatePwDots();
}

// =============================================
// [계좌 등록] API 호출
// =============================================
async function handleAddAccount(bankCode, accountNumber, paymentPassword) {
    try {
        const response = await authFetch('/api/account/register', {
            method : 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ bankCode, accountNumber, paymentPassword })
        });

        if (response.ok) {
            alert('계좌가 등록되었습니다!\n테스트 지원금 1,000,000원이 입금되었습니다.');
            closePasswordSetupModal();
            closeAccountModal();
            document.getElementById('bankCode').value = '';
            document.getElementById('accountNumberDisplay').innerText = '-';
            document.getElementById('account-preview-wrap').classList.add('hidden');
            await fetchMyAccounts(); // 목록 갱신 → 자동 선택됨
        } else {
            const msg = await response.text();
            alert('등록 실패: ' + msg);
            resetPinState();
        }
    } catch (error) {
        console.error('계좌 등록 오류:', error);
        alert('서버 통신 오류가 발생했습니다.');
        resetPinState();
    }
}

// =============================================
// [미리보기] 계좌번호 자동 생성
// =============================================
async function previewAccountNumber() {
    const bankCode = document.getElementById('bankCode').value;
    if (!bankCode) return;
    try {
        const response = await authFetch('/api/account/create', {
            method : 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ bankCode })
        });
        if (response.ok) {
            const result = await response.json();
            document.getElementById('accountNumberDisplay').innerText = result.accountNumber;
            document.getElementById('account-preview-wrap').classList.remove('hidden');
        }
    } catch (error) {
        console.error('계좌번호 생성 오류:', error);
    }
}

// =============================================
// [모임 참여] 비밀번호 확인 없이 바로 가입
// =============================================
async function submitJoinGroup() {
    if (!selectedAccountNum) return;
    try {
        const response = await authFetch('/api/group/join', {
            method : 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ inviteCode, accountNumber: selectedAccountNum, accountId: selectedAccountId })
        });
        const result = await response.json();
        if (response.ok) {
            alert('🎉 모임 가입이 완료되었습니다!');
            window.location.href = `/group/detail/${result.groupId}`;
        } else {
            alert('가입 실패: ' + (result.message || result.error || '오류가 발생했습니다.'));
        }
    } catch (error) {
        console.error('가입 오류:', error);
        alert('가입 처리 중 오류가 발생했습니다.');
    }
}

// =============================================
// 계좌 등록 모달 제어
// =============================================
function openAccountModal() {
    document.getElementById('accountModal').classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}
function closeAccountModal() {
    document.getElementById('accountModal').classList.add('hidden');
    document.body.style.overflow = 'auto';
}
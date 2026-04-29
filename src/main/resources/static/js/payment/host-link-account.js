/**
 * host-link-account.js
 * 그룹 생성 완료 후 방장의 개인계좌를 그룹멤버에 연결하는 페이지
 *
 * 흐름: create-passbook → /host-link-account?groupId=XX → /passbookfinish?groupId=XX
 */

let selectedAccountNum = null;
let selectedAccountId  = null;
let setupPin  = '';
let firstPin  = '';
let pendingBankCode      = '';
let pendingAccountNumber = '';

const groupId = document.getElementById('groupId').value;

document.addEventListener('DOMContentLoaded', async function () {
    fetchMyAccounts();
    const form = document.getElementById('addAccountForm');
    if (form) form.addEventListener('submit', onAddAccountSubmit);
});

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
        const isPrimary = acc.isPrimary === 'Y';
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

    // 주계좌 자동 선택
    const primaryAcc = accounts.find(a => a.isPrimary === 'Y');
    if (primaryAcc) {
        const firstBtn = container.querySelector('.account-item');
        if (firstBtn) selectAccount(firstBtn, primaryAcc.accountNumber, primaryAcc.accountId);
    }
}

// =============================================
// [선택] 계좌 선택
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
    selectedAccountId  = accId;
    document.getElementById('submitBtn').disabled = false;
}

// =============================================
// [계좌 등록] 폼 submit → 비밀번호 모달
// =============================================
function onAddAccountSubmit(e) {
    e.preventDefault();
    const bankCode      = document.getElementById('bankCode').value;
    const accountNumber = document.getElementById('accountNumberDisplay').innerText;

    if (!bankCode)                               { alert('은행을 선택해 주세요.'); return; }
    if (!accountNumber || accountNumber === '-') { alert('계좌번호를 먼저 생성해 주세요.'); return; }

    pendingBankCode      = bankCode;
    pendingAccountNumber = accountNumber;
    openPasswordSetupModal();
}

// =============================================
// 비밀번호 설정 모달
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
    setupPin = ''; firstPin = '';
    updatePwDots();
    const s = document.getElementById('pw-setup-status');
    if (s) { s.textContent = '숫자 6자리를 입력해주세요.'; s.className = 'text-slate-500 text-base font-bold mb-14'; }
}
function updatePwDots() {
    document.querySelectorAll('#passwordSetupModal .pw-dot').forEach((dot, i) => {
        if (i < setupPin.length) { dot.classList.add('bg-primary'); dot.classList.remove('border-2', 'border-slate-200'); }
        else                     { dot.classList.remove('bg-primary'); dot.classList.add('border-2', 'border-slate-200'); }
    });
}
function handlePwInput(num) {
    if (setupPin.length >= 6) return;
    setupPin += num;
    updatePwDots();
    if (setupPin.length === 6) {
        if (firstPin === '') {
            setTimeout(() => {
                firstPin = setupPin; setupPin = ''; updatePwDots();
                const s = document.getElementById('pw-setup-status');
                if (s) { s.textContent = '확인을 위해 한 번 더 입력해주세요.'; s.className = 'text-primary text-base font-bold mb-14'; }
            }, 300);
        } else {
            setTimeout(async () => {
                if (firstPin === setupPin) {
                    await handleAddAccount(pendingBankCode, pendingAccountNumber, firstPin);
                } else {
                    alert('비밀번호가 일치하지 않습니다. 다시 입력해주세요.');
                    resetPinState();
                }
            }, 300);
        }
    }
}
function handlePwDelete() { setupPin = setupPin.slice(0, -1); updatePwDots(); }

// =============================================
// [계좌 등록] API 호출
// =============================================
async function handleAddAccount(bankCode, accountNumber, paymentPassword) {
    try {
        const response = await authFetch('/api/account/register', {
            method: 'POST',
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
            await fetchMyAccounts();
        } else {
            alert('등록 실패: ' + await response.text());
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
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ bankCode })
        });
        if (response.ok) {
            const result = await response.json();
            document.getElementById('accountNumberDisplay').innerText = result.accountNumber;
            document.getElementById('account-preview-wrap').classList.remove('hidden');
        }
    } catch (error) { console.error('계좌번호 생성 오류:', error); }
}

// =============================================
// [연결 완료] GROUP_MEMBERS ACCOUNT_ID 업데이트 → passbookfinish 이동
// =============================================
async function submitHostAccount() {
    if (!selectedAccountNum || !selectedAccountId) return;

    try {
        const response = await authFetch('/api/group/host-account', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                groupId   : parseInt(groupId),
                accountId : selectedAccountId
            })
        });

        if (response.ok) {
            window.location.href = `/passbookfinish?groupId=${groupId}`;
        } else {
            const msg = await response.text();
            alert('연결 실패: ' + msg);
        }
    } catch (error) {
        console.error('계좌 연결 오류:', error);
        alert('서버 통신 오류가 발생했습니다.');
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
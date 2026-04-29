// =====================================================================
// PIN 전역 상태
// =====================================================================
let _pinCurrent   = '';  // 현재 입력 중인 PIN
let _pinFirst     = '';  // 첫 번째 입력값 저장 (재확인용)
let _pendingBankCode      = '';  // PIN 완료 후 등록에 사용할 은행코드
let _pendingAccountNumber = '';  // PIN 완료 후 등록에 사용할 계좌번호

// =====================================================================
// DOMContentLoaded
// =====================================================================
document.addEventListener('DOMContentLoaded', () => {
    fetchMyAccounts();

    const addAccountForm = document.getElementById('addAccountForm');
    if (addAccountForm) {
        addAccountForm.addEventListener('submit', handleAddAccount);
    }
});

// =====================================================================
// [조회] 내 모든 계좌 목록
// =====================================================================
async function fetchMyAccounts() {
    const container = document.getElementById('account-list-container');
    try {
        const response = await authFetch('/api/account/list');
        if (!response.ok) throw new Error('계좌 목록을 불러오지 못했습니다.');
        const accounts = await response.json();
        renderAccounts(accounts);
    } catch (error) {
        console.error('Error:', error);
        if (container) {
            container.innerHTML = `<p class="text-center text-red-500 py-10">데이터를 불러오는데 실패했습니다.</p>`;
        }
    }
}

// =====================================================================
// [렌더링] 계좌 리스트
// =====================================================================
function renderAccounts(accounts) {
    const container = document.getElementById('account-list-container');
    if (!container) return;

    if (!accounts || accounts.length === 0) {
        container.innerHTML = `
            <div class="py-20 text-center bg-white dark:bg-slate-900 rounded-[2.5rem] border border-dashed border-slate-200 dark:border-slate-800">
                <span class="material-symbols-outlined text-5xl text-slate-200 mb-3">account_balance_wallet</span>
                <p class="text-slate-400 text-sm">연결된 계좌가 없습니다.<br>새 계좌를 추가해 보세요!</p>
            </div>`;
        return;
    }

    container.innerHTML = accounts.map(acc => {
        const isPrimary = acc.isDefault === 'Y';
        return `
        <div class="relative p-6 bg-white dark:bg-slate-900 rounded-[2rem] shadow-sm border-2 ${isPrimary ? 'border-primary' : 'border-slate-100 dark:border-slate-800'} mb-4 transition-all hover:shadow-md">
            <div class="flex justify-between items-start mb-4">
                <div class="flex gap-4">
                    <div class="w-14 h-14 rounded-2xl ${isPrimary ? 'bg-primary' : 'bg-slate-900'} flex items-center justify-center text-white flex-shrink-0">
                        <span class="material-symbols-outlined !text-3xl">account_balance</span>
                    </div>
                    <div>
                        <div class="flex items-center gap-2 mb-1">
                            <h3 class="font-bold text-lg text-slate-900 dark:text-white">${acc.bankName}</h3>
                            ${isPrimary ? '<span class="px-2 py-0.5 bg-primary text-white text-[10px] font-bold rounded-md">주계좌</span>' : ''}
                        </div>
                        <p class="text-xs text-slate-500 font-mono">${acc.accountNumber}</p>
                        <p class="text-sm font-bold text-slate-700 dark:text-slate-300 mt-1">잔액: ${Number(acc.balance).toLocaleString()}원</p>
                    </div>
                </div>
                ${isPrimary
                    ? `<div class="p-2 text-primary"><span class="material-symbols-outlined" style="font-variation-settings:'FILL' 1;">check_circle</span></div>`
                    : `<button onclick="setPrimaryAccount(${acc.accountId})"
                            class="px-3 py-1.5 text-xs font-bold text-slate-500 bg-slate-100 dark:bg-slate-800 rounded-xl hover:bg-primary hover:text-white transition-colors whitespace-nowrap">
                            주계좌 설정
                       </button>`
                }
            </div>
            <div class="flex gap-2 pt-4 border-t border-slate-50 dark:border-slate-800">
                ${!isPrimary
                    ? `<button onclick="setPrimaryAccount(${acc.accountId})"
                            class="flex-1 py-3 text-xs font-bold text-primary bg-primary/10 rounded-xl hover:bg-primary/20 transition-colors">
                            주계좌로 설정
                       </button>`
                    : `<div class="flex-1 py-3 text-xs font-bold text-center text-primary">
                            ✓ 현재 주계좌 (회비 출금·잔액 표시 계좌)
                       </div>`
                }
                <button onclick="deleteAccount(${acc.accountId}, '${acc.bankName}', ${isPrimary})"
                        class="flex-1 py-3 text-xs font-bold text-red-500 bg-red-50 dark:bg-red-900/10 rounded-xl hover:bg-red-100 transition-colors">
                    삭제
                </button>
            </div>
        </div>`;
    }).join('');
}

// =====================================================================
// [주계좌 설정]
// =====================================================================
async function setPrimaryAccount(accountId) {
    if (!confirm('이 계좌를 주계좌로 설정하시겠습니까?\n그룹 메인 잔액 표시 및 회비 출금이 이 계좌로 처리됩니다.')) return;
    try {
        const response = await authFetch(`/api/account/${accountId}/set-primary`, { method: 'PATCH' });
        if (response.ok) {
            fetchMyAccounts();
        } else {
            alert('주계좌 설정에 실패했습니다.');
        }
    } catch (error) {
        console.error('주계좌 설정 에러:', error);
    }
}

// =====================================================================
// [등록] "계좌 인증 요청" 버튼 클릭 → PIN 모달 열기
// =====================================================================
function handleAddAccount(e) {
    e.preventDefault();

    const bankCode      = document.getElementById('bankCode').value;
    const accountNumber = document.getElementById('accountNumberDisplay').innerText;

    if (!bankCode)                               { alert('은행을 선택해 주세요.'); return; }
    if (!accountNumber || accountNumber === '-') { alert('계좌번호를 먼저 생성해 주세요.'); return; }

    // 은행코드·계좌번호 임시 저장 후 PIN 모달 오픈
    _pendingBankCode      = bankCode;
    _pendingAccountNumber = accountNumber;
    openPinModal();
}

// =====================================================================
// PIN 모달 제어
// =====================================================================
function openPinModal() {
    resetPinState();
    const modal = document.getElementById('pinModal');
    if (modal) {
        modal.classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    }
}

function closePinModal() {
    const modal = document.getElementById('pinModal');
    if (modal) {
        modal.classList.add('hidden');
        document.body.style.overflow = 'auto';
    }
    resetPinState();
}

function resetPinState() {
    _pinCurrent = '';
    _pinFirst   = '';
    updatePinDots();
    const statusEl = document.getElementById('pin-status-text');
    if (statusEl) {
        statusEl.textContent = '숫자 6자리를 입력해주세요.';
        statusEl.classList.remove('text-primary');
    }
}

// =====================================================================
// PIN 키패드 입력 / 삭제 / 점 업데이트
// =====================================================================
function pinInput(num) {
    if (_pinCurrent.length >= 6) return;

    _pinCurrent += num;
    updatePinDots();

    if (_pinCurrent.length === 6) {
        if (_pinFirst === '') {
            // 첫 번째 입력 완료 → 재확인 단계로
            setTimeout(() => {
                _pinFirst   = _pinCurrent;
                _pinCurrent = '';
                updatePinDots();
                const statusEl = document.getElementById('pin-status-text');
                if (statusEl) {
                    statusEl.textContent = '한 번 더 입력해 확인해 주세요.';
                    statusEl.classList.add('text-primary');
                }
            }, 200);
        } else {
            // 재확인 입력 완료 → 일치 검사
            setTimeout(() => {
                if (_pinFirst === _pinCurrent) {
                    submitAccountRegistration(_pinFirst);
                } else {
                    alert('비밀번호가 일치하지 않습니다. 다시 입력해 주세요.');
                    resetPinState();
                }
            }, 200);
        }
    }
}

function pinDelete() {
    _pinCurrent = _pinCurrent.slice(0, -1);
    updatePinDots();
}

function updatePinDots() {
    const dots = document.querySelectorAll('.pin-dot');
    dots.forEach((dot, i) => {
        if (i < _pinCurrent.length) {
            dot.classList.add('bg-primary', 'border-primary');
            dot.classList.remove('border-slate-200');
        } else {
            dot.classList.remove('bg-primary', 'border-primary');
            dot.classList.add('border-slate-200');
        }
    });
}

// =====================================================================
// [최종] 서버에 계좌 등록 (AccountDTO.RegisterRequest)
// =====================================================================
async function submitAccountRegistration(paymentPassword) {
    try {
        const response = await authFetch('/api/account/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                bankCode: _pendingBankCode,
                paymentPassword: paymentPassword
            })
        });

        if (response.ok) {
            closePinModal();
            closeAccountModal();
            alert('계좌가 성공적으로 연결되었습니다.');
            fetchMyAccounts();
        } else {
            const errorMsg = await response.text();
            alert('등록 실패: ' + errorMsg);
            resetPinState();
        }
    } catch (error) {
        console.error('등록 에러:', error);
        alert('연결이 원활하지 않습니다.');
        resetPinState();
    }
}

// =====================================================================
// [미리보기] 은행 선택 시 계좌번호 자동 생성
// =====================================================================
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
    } catch (error) {
        console.error('계좌번호 생성 에러:', error);
    }
}

// =====================================================================
// [삭제] 계좌 삭제
// =====================================================================
async function deleteAccount(accountId, bankName, isPrimary) {
    if (isPrimary) {
        alert('주계좌는 삭제할 수 없습니다.\n다른 계좌를 주계좌로 먼저 설정해 주세요.');
        return;
    }
    if (!confirm(`정말로 ${bankName} 계좌를 삭제하시겠습니까?`)) return;
    try {
        const response = await authFetch(`/api/account/${accountId}`, { method: 'DELETE' });
        if (response.ok) {
            fetchMyAccounts();
        } else {
            alert('삭제 실패');
        }
    } catch (error) {
        console.error('삭제 에러:', error);
    }
}

// =====================================================================
// 계좌 등록 모달 열기 / 닫기
// =====================================================================
function openAccountModal() {
    const modal = document.getElementById('accountModal');
    if (modal) modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function closeAccountModal() {
    const modal = document.getElementById('accountModal');
    if (modal) modal.classList.add('hidden');
    document.body.style.overflow = 'auto';
    // 폼 초기화
    const form = document.getElementById('addAccountForm');
    if (form) form.reset();
    const preview = document.getElementById('account-preview-wrap');
    if (preview) preview.classList.add('hidden');
    const display = document.getElementById('accountNumberDisplay');
    if (display) display.innerText = '-';
}
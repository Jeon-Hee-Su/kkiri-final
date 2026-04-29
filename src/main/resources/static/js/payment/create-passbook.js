/** 전역 변수 설정 */
let selectedBankCode = ""; 
let setupPin = "";    // 현재 입력 중인 번호
let firstPin = "";    // 첫 번째 입력 번호 저장용

document.addEventListener('DOMContentLoaded', () => {
    
    // 1. 이전 페이지에서 저장한 모임명 가져와서 표시
    const tempGroupName = sessionStorage.getItem('tempGroupName') || "새로운 모임";
    const nameDisplay = document.getElementById('display-group-name');
    if (nameDisplay) {
        nameDisplay.innerText = tempGroupName;
    }
    
    // [1] 커스텀 드롭다운 관련 요소
    const trigger = document.getElementById('bank-selector-trigger');
    const optionsList = document.getElementById('bank-options');
    const displaySpan = document.getElementById('selected-bank-display');
    const icon = document.getElementById('dropdown-icon');
    const optionItems = document.querySelectorAll('#bank-options li');

    // [2] 약관 동의 관련 요소
    const checkAll = document.getElementById('check-all');
    const agreeItems = document.querySelectorAll('.agree-item');

    // --- 드롭다운 제어 로직 ---
    if (trigger) {
        trigger.onclick = function(e) {
            e.stopPropagation();
            optionsList.classList.toggle('hidden');
            if (icon) icon.classList.toggle('rotate-180');
            trigger.classList.toggle('border-primary');
        };
    }

    optionItems.forEach(item => {
        item.onclick = function(e) {
            const val = this.getAttribute('data-value');
            const name = this.innerText;

            selectedBankCode = val; 
            displaySpan.innerText = name;
            displaySpan.classList.remove('text-slate-400');
            displaySpan.classList.add('text-slate-900');

            optionsList.classList.add('hidden');
            if (icon) icon.classList.remove('rotate-180');
            trigger.classList.remove('border-primary');
        };
    });

    // 드롭다운 외부 클릭 시 닫기
    window.addEventListener('click', (e) => {
        if (trigger && !trigger.contains(e.target)) {
            optionsList.classList.add('hidden');
            if (icon) icon.classList.remove('rotate-180');
            if (trigger) trigger.classList.remove('border-primary');
        }
    });

    // --- 약관 전체 동의 로직 ---
    if (checkAll && agreeItems.length > 0) {
        checkAll.addEventListener('change', (e) => {
            agreeItems.forEach(item => { item.checked = e.target.checked; });
        });
        agreeItems.forEach(item => {
            item.addEventListener('change', () => {
                checkAll.checked = Array.from(agreeItems).every(i => i.checked);
            });
        });
    }
});

/** [STEP 1] 본인인증 및 입력 검증 */
async function handleFinish() {
    const agreeItems = document.querySelectorAll('.agree-item');
    const isAllAgreed = Array.from(agreeItems).every(item => item.checked);
    
    if (!selectedBankCode) { alert('은행을 선택해 주세요.'); return; }
    if (!isAllAgreed) { alert('필수 약관에 모두 동의해야 합니다.'); return; }

    const IMP = window.IMP;
    IMP.init("imp73256801"); // 포트원 가맹점 식별코드
    IMP.certification({
        pg: "inicis_unified",
        merchant_uid: `moim_cert_${new Date().getTime()}`,
        popup: true
    }, (rsp) => {
        if (rsp.success) {
            sessionStorage.setItem('certUid', rsp.imp_uid);
            
            // 암호 입력 모달 열기
            const pwdModal = document.getElementById('password-modal');
            if (pwdModal) {
                resetPinState();
                pwdModal.classList.remove('hidden');
                pwdModal.style.display = 'flex';
                document.body.style.overflow = 'hidden';
            }
        } else {
            alert(`본인인증 실패: ${rsp.error_msg}`);
        }
    });
}

/** [STEP 2] 암호 입력 처리 */
function handlePwInput(num) {
    if (setupPin.length < 6) {
        setupPin += num;
        updatePwDots();

        if (setupPin.length === 6) {
            if (firstPin === "") {
                // 첫 번째 입력 완료 -> 확인 단계로
                setTimeout(() => {
                    firstPin = setupPin;
                    setupPin = "";
                    updatePwDots();
                    const statusText = document.getElementById('password-status');
                    if (statusText) {
                        statusText.innerText = "확인을 위해 한 번 더 입력해주세요.";
                        statusText.classList.add('text-primary', 'font-bold');
                    }
                }, 300);
            } else {
                // 재확인 입력 완료 시 대조
                if (firstPin === setupPin) {
                    submitAccountCreation(); 
                } else {
                    setTimeout(() => {
                        alert("비밀번호가 일치하지 않습니다. 다시 입력해주세요.");
                        resetPinState();
                    }, 200);
                }
            }
        }
    }
}

/** 핀번호 관련 유틸리티 함수 */
function resetPinState() {
    setupPin = "";
    firstPin = "";
    updatePwDots();
    const statusText = document.getElementById('password-status');
    if (statusText) {
        statusText.innerText = "숫자 6자리를 입력해주세요.";
        statusText.classList.remove('text-primary', 'font-bold');
    }
}

function updatePwDots() {
    const dots = document.querySelectorAll('.pw-dot');
    dots.forEach((dot, index) => {
        if (index < setupPin.length) {
            dot.classList.add('active', 'bg-primary');
            dot.classList.remove('border-2', 'border-slate-200');
        } else {
            dot.classList.remove('active', 'bg-primary');
            dot.classList.add('border-2', 'border-slate-200');
        }
    });
}

function handlePwDelete() {
    setupPin = setupPin.slice(0, -1);
    updatePwDots();
}
/** [STEP 3] 서버 전송 및 완료 페이지 이동 */
async function submitAccountCreation() {
    // 세션 데이터 정리 (1단계에서 저장된 값들)
    const savedCategory = sessionStorage.getItem('tempGroupCategory') || "기타";
    const savedGroupName = sessionStorage.getItem('tempGroupName');
    const certUid = sessionStorage.getItem('certUid');

    const requestData = {
        bankCode: selectedBankCode,
        paymentPassword: firstPin, 
        groupName: savedGroupName,
        category: savedCategory, // 서버로 전송
        certUid: certUid
    };

    try {
        const token = localStorage.getItem("accessToken");
        const response = await authFetch('/api/group/create', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'accessToken': token 
            },
            credentials: 'include', 
            body: JSON.stringify(requestData)
        });

        if (response.ok) {
            const result = await response.json();
            const selectedBankName = document.getElementById('selected-bank-display').innerText;
            
            // ✅ [수정] 서버에서 돌려준 정보를 세션에 저장 (완료 페이지용)
            sessionStorage.setItem('createdAccountNumber', result.accountNumber);
            sessionStorage.setItem('createdBankName', selectedBankName);
            sessionStorage.setItem('finalCategory', result.category); // 서버 응답에 포함된 카테고리

            if (result.groupId) {
                // ✅ 성공 시 '임시' 데이터만 삭제 (완료 페이지에서 쓸 데이터는 남김)
                sessionStorage.removeItem('tempGroupCategory');
                sessionStorage.removeItem('tempGroupName');
                sessionStorage.removeItem('certUid');
                
                // URL에 카테고리를 포함해서 넘기면 더 확실합니다.
                // ★ passbookfinish 전에 방장 개인계좌 연결 페이지로 이동
                location.href = `/host-link-account?groupId=${result.groupId}`;
            } else {
                location.href = '/passbookfinish';
            }
        } else {
            const errorMsg = await response.text();
            console.error("서버 응답 에러:", errorMsg);
            alert("계좌 생성 중 오류가 발생했습니다.");
            resetPinState();
        }
    } catch (error) {
        console.error("네트워크 오류:", error);
        alert("연결이 원활하지 않습니다.");
    }
}
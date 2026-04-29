/** 전역 변수 */
let targetCheckboxId = null; 
let verifiedCi = null;
let isCertified = false;

document.addEventListener('DOMContentLoaded', () => {
    const signupForm = document.getElementById('signupForm');
    const checkAll = document.getElementById('check-all');
    const agreeItems = document.querySelectorAll('.agree-item');
    const phoneInput = document.getElementById('phoneNumber');
    
    const pwInput = document.getElementById('password');
    const pwConfirmInput = document.getElementById('passwordConfirm');
    
    // HTML ID와 정확히 매칭
    const pwValidationMsg = document.getElementById('pwValidationMsg'); // 상단: 8자 체크용
    const pwCheckMsg = document.getElementById('pwCheckMsg');           // 하단: 일치 체크용

	// 이메일 형식 체크
	const emailInput = document.getElementById('email');
	const emailMsg = document.getElementById('emailCheckMsg');
	
    // --- 1. 약관 전체 동의 로직 ---
    // (기존 코드와 동일)
    if (checkAll) {
        checkAll.addEventListener('change', (e) => {
            agreeItems.forEach(item => item.checked = e.target.checked);
        });
        agreeItems.forEach(item => {
            item.addEventListener('change', updateCheckAllStatus);
        });
    }

    // --- 2. 전화번호 자동 하이픈 ---
    // (기존 코드와 동일)
    phoneInput?.addEventListener('input', (e) => {
        let val = e.target.value.replace(/[^0-9]/g, '');
        if (val.length <= 3) e.target.value = val;
        else if (val.length <= 7) e.target.value = val.slice(0, 3) + '-' + val.slice(3);
        else e.target.value = val.slice(0, 3) + '-' + val.slice(3, 7) + '-' + val.slice(7, 11);
    });

    // --- 3. 비밀번호 실시간 체크 (통합 및 정리) ---

    // 3-1. 첫 번째 비밀번호: 형식 검사
    function checkPasswordValidation() {
        const pw = pwInput.value;
        // 영문+숫자 포함 8자 이상 정규식
        const pwReg = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>])[A-Za-z\d!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>]{8,16}$/;

        if (pw === "") {
            pwValidationMsg.textContent = "";
        } else if (!pwReg.test(pw)) {
            pwValidationMsg.textContent = "✕ 8~16자, 영문/숫자/특수문자를 조합해주세요.";
            pwValidationMsg.className = "text-xs mt-1 ml-1 font-semibold text-red-600 h-4";
        } else {
            pwValidationMsg.textContent = "✓ 안전한 비밀번호입니다.";
            pwValidationMsg.className = "text-xs mt-1 ml-1 font-semibold text-green-600 h-4";
        }
        // 위 칸 수정 시 아래 일치 여부도 실시간 반영
        checkPasswordMatch();
    }

    // 3-2. 두 번째 비밀번호 확인: 일치 여부 검사
    function checkPasswordMatch() {
        const pw = pwInput.value;
        const confirm = pwConfirmInput.value;

        if (confirm === "") {
            pwCheckMsg.textContent = "";
        } else if (pw === confirm) {
            pwCheckMsg.textContent = "✓ 비밀번호가 일치합니다.";
            pwCheckMsg.className = "text-xs mt-1 ml-1 font-semibold text-green-600 h-4";
        } else {
            pwCheckMsg.textContent = "✕ 비밀번호가 일치하지 않습니다.";
            pwCheckMsg.className = "text-xs mt-1 ml-1 font-semibold text-red-600 h-4";
        }
    }

	// --- 4. 이메일 형식 실시간 체크 ---
	function checkEmailValidation() {
		const email = emailInput.value;
		// 이메일 정규식
		const emailReg = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

		if (email === "") {
			emailMsg.textContent = "";
		} else if (!emailReg.test(email)) {
			emailMsg.textContent = "✕ 올바른 이메일 형식이 아닙니다.";
			emailMsg.className = "text-xs mt-1 ml-1 font-semibold text-red-600 h-4";
		} else {
			emailMsg.textContent = "✓ 사용 가능한 이메일 형식입니다.";
			emailMsg.className = "text-xs mt-1 ml-1 font-semibold text-green-600 h-4";
		}
	}

	
	// --- 5. 폼 제출 로직 ---
    // 이벤트 연결 (기존 validatePassword 호출부 삭제하고 아래로 교체)
    pwInput?.addEventListener('input', checkPasswordValidation);
    pwConfirmInput?.addEventListener('input', checkPasswordMatch);
	emailInput?.addEventListener('input', checkEmailValidation);
    signupForm?.addEventListener('submit', async (e) => {
        e.preventDefault();
		
		if (!isCertified) {
			alert("서비스 이용을 위해 먼저 본인인증을 완료해 주세요.");
			return;	
		}

        // 최종 유효성 검사 (정규식 포함)
        const pwReg = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>])[A-Za-z\d!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>]{8,16}$/;
        if (!pwReg.test(pwInput.value)) {
            alert("비밀번호를 8자리 이상의 영문+숫자+특수문자 조합으로 입력해주세요.");
            pwInput.focus();
            return;
        }
        
        if (pwInput.value !== pwConfirmInput.value) {
            alert("비밀번호가 일치하지 않습니다.");
            pwConfirmInput.focus();
            return;
        }

        const allRequiredChecked = Array.from(agreeItems).every(i => i.checked);
        if (!allRequiredChecked) {
            alert("필수 약관에 모두 동의해주세요.");
            return;
        }

        // 데이터 전송 로직 (기존과 동일)
        const signupData = {
            loginId: document.getElementById('loginId')?.value || '', 
            password: pwInput.value,
            name: document.getElementById('name')?.value || '',
            birthDate: document.getElementById('birth')?.value || '',
            phoneNumber: phoneInput?.value || '',
            email: document.getElementById('email')?.value || '',
            ci: verifiedCi // verifiedCi 변수가 정의되어 있는지 확인 필요
        };

        try {
            const response = await fetch('/api/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(signupData)    
            });

            if (response.ok) {
                alert("회원가입이 완료되었습니다! 로그인 페이지로 이동합니다.");
                window.location.href = '/auth/login'; 
            } else {
                const errorMsg = await response.text();
                alert("가입 실패: " + errorMsg);
            }
        } catch (error) {
            alert("서버 통신 중 오류가 발생했습니다.");
        }
    });
});
/** 전체 동의 상태 업데이트 함수 */
function updateCheckAllStatus() {
    const checkAll = document.getElementById('check-all');
    const agreeItems = document.querySelectorAll('.agree-item');
    if (checkAll) {
        checkAll.checked = Array.from(agreeItems).every(i => i.checked);
    }
}

/** [수정] 아이디 중복 확인 (POST 방식으로 변경하여 405 에러 해결) */
/** [최종 수정] 아이디 중복 확인 (서버 @RequestParam 대응) */
async function checkIdDup() {
    const loginIdField = document.getElementById('loginId');
    const loginId = loginIdField.value.trim();
    const idMsg = document.getElementById('idCheckMsg');
    
    if (!loginId) {
        idMsg.textContent = "! 아이디를 입력해주세요.";
        idMsg.className = "text-xs mt-1 font-semibold text-orange-500";
        return;
    }

    try {
		const response = await fetch(`/api/auth/check-id`, { // 경로 주의 (아래 설명 참조)
		            method: 'POST',
		            headers: {
		                'Content-Type': 'application/json'
		            },
		            body: JSON.stringify({ loginId: loginId }) // JSON 데이터로 전송
		        });
        
        if (!response.ok) {
            throw new Error(`Status: ${response.status}`);
        }

        const isAvailable = await response.json(); 

        if (isAvailable) {
            idMsg.textContent = "✓ 사용 가능한 아이디입니다.";
            idMsg.className = "text-xs mt-1 font-semibold text-green-600";
        } else {
            idMsg.textContent = "✕ 이미 사용 중인 아이디입니다.";
            idMsg.className = "text-xs mt-1 font-semibold text-red-600";
        }
    } catch (error) {
        idMsg.textContent = "✕ 중복 확인 실패 (전송 형식 확인 필요)";
        idMsg.className = "text-xs mt-1 font-semibold text-red-600";
        console.error("중복 확인 에러 상세:", error);
    }
}
/** 모달 및 약관 자동 체크 제어 */
function openModal(id) {
    targetCheckboxId = id; // 호출 시 'agree1' 또는 'agree2'를 넘겨받음
    document.getElementById('modal-privacy')?.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function closeModalWithCheck() {
    if (targetCheckboxId) {
        const checkbox = document.getElementById(targetCheckboxId);
        if (checkbox) {
            checkbox.checked = true; // [핵심] 확인 클릭 시 해당 약관 체크
            updateCheckAllStatus();  // 전체 동의 상태도 갱신
        }
    }
    closeModal();
}

function closeModal() {
    document.getElementById('modal-privacy')?.classList.add('hidden');
    document.body.style.overflow = 'auto';
    targetCheckboxId = null;
}
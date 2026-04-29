/** 전역 변수 */
let isCertified = false;
let targetCheckboxId = null;

/** [공통] 전체 동의 상태 업데이트 함수 */
function updateCheckAllStatus() {
	const checkAll = document.getElementById('check-all');
	const agreeItems = document.querySelectorAll('.agree-item');
	if (checkAll) {
		checkAll.checked = Array.from(agreeItems).every(i => i.checked);
	}
}

document.addEventListener('DOMContentLoaded', () => {
	const urlParams = new URLSearchParams(window.location.search);
	const tempToken = urlParams.get('token');

	const signupForm = document.getElementById('signupForm');
	const checkAll = document.getElementById('check-all');
	const agreeItems = document.querySelectorAll('.agree-item');
	const phoneInput = document.getElementById('phoneNumber');
	const nameInput = document.getElementById('name');
	const birthInput = document.getElementById('birth');

	// --- 토큰 존재 시 자동 입력 로직 ---
	if (tempToken) {
		try {
			const base64Url = tempToken.split('.')[1];
			let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
			while (base64.length % 4) { base64 += '='; }

			const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
				return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
			}).join(''));

			const payload = JSON.parse(jsonPayload);
			const userEmail = payload.email || payload.sub;

			if (userEmail) {
				if (document.getElementById('display-email')) document.getElementById('display-email').textContent = userEmail;
				if (document.getElementById('hidden-email')) document.getElementById('hidden-email').value = userEmail;
			}
		} catch (e) {
			console.error("소셜 데이터 디코딩 중 오류:", e);
		}
	}

	// --- 1. 약관 동의 제어 ---
	if (checkAll) {
		checkAll.addEventListener('change', (e) => {
			agreeItems.forEach(item => { item.checked = e.target.checked; });
		});
		agreeItems.forEach(item => {
			item.addEventListener('change', updateCheckAllStatus);
		});
	}

	// --- 2. 전화번호 자동 하이픈 ---
	phoneInput?.addEventListener('input', (e) => {
		let val = e.target.value.replace(/[^0-9]/g, '');
		if (val.length <= 3) e.target.value = val;
		else if (val.length <= 7) e.target.value = val.slice(0, 3) + '-' + val.slice(3);
		else e.target.value = val.slice(0, 3) + '-' + val.slice(3, 7) + '-' + val.slice(7, 11);
	});

	// --- 3. 폼 제출 로직 (핵심 수정) ---
	signupForm?.addEventListener('submit', async (e) => {
		e.preventDefault();

		if (!isCertified) {
			alert("서비스 이용을 위해 먼저 본인인증을 완료해 주세요.");
			return;
		}

		const allRequiredChecked = Array.from(agreeItems).every(i => i.checked);
		if (!allRequiredChecked) {
			alert("필수 약관에 모두 동의해주세요.");
			return;
		}

		if (!tempToken) {
			alert("유효하지 않은 가입 세션입니다. 다시 로그인해 주세요.");
			return;
		}


		const signupData = {
			name: nameInput.value,
			birthDate: birthInput.value,
			phoneNumber: phoneInput.value,
			email: document.getElementById('hidden-email')?.value,
			tempToken: tempToken,
			ci: verifiedCi,
			certUid: sessionStorage.getItem('certUid')

		};

		console.log("🚀 서버로 전송할 데이터:", signupData);
		console.log("📍 현재 URL의 토큰:", tempToken);

		try {
			const response = await fetch('/api/auth/complete-social-signup', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				credentials: 'include',
				body: JSON.stringify(signupData)
			});

			if (response.ok) {
				sessionStorage.removeItem('certUid'); // 가입 완료 후 임시 데이터 삭제

				alert("회원가입이 완료되었습니다! 로그인페이지로 이동합니다.");
				window.location.href = '/auth/login';
			} else {
				const data = await response.json();
				alert(data.message || "회원가입 처리 중 오류가 발생했습니다.");
			}
		} catch (error) {
			console.error('Error:', error);
			alert(error.message);
		}
	});
});

// --- 4. 팝업 모달 제어 (전역 함수) ---
function openModal(type) {
	document.getElementById('modal-privacy')?.classList.remove('hidden');
	document.body.style.overflow = 'hidden';
}

function openModal(id) {
	targetCheckboxId = id;
	document.getElementById('modal-privacy')?.classList.remove('hidden');
	document.body.style.overflow = 'hidden';
}

function closeModalWithCheck() {
	if (targetCheckboxId) {
		const checkbox = document.getElementById(targetCheckboxId);
		if (checkbox) {
			checkbox.checked = true;
			updateCheckAllStatus();
		}
	}
	closeModal();
}

function closeModal() {
	document.getElementById('modal-privacy')?.classList.add('hidden');
	document.body.style.overflow = 'auto';
	targetCheckboxId = null;
}


document.addEventListener('DOMContentLoaded', () => {
	const pwChangeForm = document.getElementById('pwchangeform')
	const newpw = document.getElementById('newpw');
	const confirmpw = document.getElementById('confirmpw');
	const textbox = document.getElementById('pwmessage');
	const submitbtn = document.getElementById('submit-btn');
	
	const nameci = document.getElementById('name');
	const birthci = document.getElementById('birth');
	
	function validatePasswords() {
		const pw = newpw.value;
		const conpw = confirmpw.value;
		const pwRegex = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>])[A-Za-z\d!@#$%^&*(),.?":{}|<>₩[\]\-_+=~`\\/ ;'<>]{8,16}$/;
		
		let isValid = false;
		
		// 본인인증여부확인
		if(typeof isCertified !== 'undefined' && !isCertified) {
			textbox.innerHTML = `<strong>인증필요 : </strong> 본인인증을 먼저 완료해 주세요`;
			textbox.className = "text-sm text-red-600 leading-relaxed";
			return;
		}
		
		if(pw === "") {
			textbox.innerHTML = `<strong>빈칸 :</strong> 비밀번호를 입력하여 주십시오.`;
			textbox.className = "text-rs text-red-600 leading-relaxed";
		} else if(!pwRegex.test(pw)) {
			textbox.innerHTML = `<strong>형식 불일치 :</strong> 비밀번호는 소/대문자, 숫자 및 특수문자를 포함하여 8~16자로 작성하여야합니다.`;
			textbox.className =	"text-rs text-red-600 leading-relaxed";
		} else if(conpw !== "" && pw !== conpw) {
			textbox.innerHTML = `<strong>비밀번호 불일치 :</strong> 비밀번호 재확인 칸을 확인해 주십시오.`;
			textbox.className =	"text-rs text-red-600 leading-relaxed";
		} else if(pw === conpw) {
			textbox.innerHTML = `<strong>확인 완료</strong> 비밀번호가 일치합니다.`;
			textbox.className =	"text-rs text-green-600 leading-relaxed";
			isValid = true;
		} else {
			textbox.innerHTML = `<strong>빈칸</strong> 비밀번호를 확인 해주십시오.`;
			textbox.className =	"text-rs text-red-600 leading-relaxed";
		}
		submitbtn.disabled = !isValid;
	}
	newpw.addEventListener('input', validatePasswords);
	confirmpw.addEventListener('input', validatePasswords);
	
	
	pwChangeForm.addEventListener('submit', async(e) => {
		e.preventDefault();
		// 제출페이지 새로고침 금지

		const formData = {
			name: nameci.value,
			birth: birthci.value,
			newpw: newpw.value,
			impUid: sessionStorage.getItem('certUid')
		};
		
		try{
			const response = await authFetch('/api/user/change-password', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify(formData)
			});
			
			if(response.ok) {
				alert('비밀번호가 성공적으로 변경되었습니다. 다시 로그인해주세요.');
					
				// 로그인 페이지 이동
				window.location.href = "/auth/login";
			} else {
				const errorData = await response.json();
				alert('변경 실패: ' + (errorData.message || '현재 비밀번호를 확인해주세요.'));
			}
		} catch(error) {
			console.error('Error: ', error);
			alert('서버와의 통신 중 오류가 발생했습니다. 나중에 다시 시도해 주십시오.');
		}
	});
});
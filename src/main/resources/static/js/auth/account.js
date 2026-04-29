/**
 * account.js - 프로필 조회 및 수정 전용
 */

document.addEventListener('DOMContentLoaded', async () => {
    // 페이지 로드 시 즉시 서버로 프로필 정보를 요청
    // (쿠키는 브라우저가 자동으로 요청 헤더에 담아서 보냄)
    await loadProfileDetails();
});

// 1. DB에서 비동기로 상세정보 가져오기
async function loadProfileDetails() {
    try {
        const response = await authFetch('/api/user/profile/details',{
			credentials: 'include'
		});
		
        if (response.ok) {
            const data = await response.json();
            console.log("DB 정보 로드 성공: ", data);
            updateProfileUI(data);
        } else if (response.status === 401 || response.status === 403) {
            // auth-common.js에서 처리하지만, 개별 페이지에서도 안전장치로 유지
            alert("로그인이 필요한 서비스입니다.");
            location.href = "/login";
        }
    } catch (error) {
        console.error("데이터 로드 중 오류 발생 : ", error);
    }
}

// 2. 데이터를 화면 UI에 반영
function updateProfileUI(data) {
    const fields = {
        'topname': data.nickname || data.name,
        'toprole': data.role || '사용자',
        'info-userid': data.userId,
        'info-name': data.name,
        'info-email': data.email,
        'info-phone': data.phone || '연락처 미등록',
        'info-birth': data.birth || '생년월일 미등록',
        'info-nick': data.nickname || '닉네임 미등록'
    };

    // 텍스트 필드 업데이트
    Object.keys(fields).forEach(id => {
        const el = document.getElementById(id);
        if (el && fields[id] !== undefined) {
            el.innerText = fields[id];
        }
    });

    // 프로필 이미지 처리
    const profileImg = document.getElementById('profileimg');
    if (profileImg) {
        profileImg.src = data.profileImage || "/img/default_profile.jpg";
        profileImg.onerror = () => { profileImg.src = "/img/default_profile.jpg"; };
    }

    // 수정 모드 input 필드 미리 채우기
    const inputNames = ['userId', 'name', 'nickname', 'email', 'phone', 'birth'];
    inputNames.forEach(name => {
        const input = document.querySelector(`input[name="${name}"]`);
        if (input) {
            // 서버 데이터 키값(phone, birth)에 맞춰서 매핑
            input.value = data[name] || ''; 
        }
    });
}

// 3. 수정 모드 토글 함수
let isEditMod = false;
function toggleProfileEdit() {
    const viewMod = document.querySelectorAll('.view-mode');
    const editMod = document.querySelectorAll('.edit-mode');
    const btnText = document.getElementById('editText');
    const btnIcon = document.getElementById('editIcon');

    if (!isEditMod) {
        // 수정 모드로 전환
        viewMod.forEach(el => el.classList.add('hidden'));
        editMod.forEach(el => el.classList.remove('hidden'));
        btnText.innerText = "정보저장";
        btnIcon.innerText = "save";
        isEditMod = true;
    } else {
        // 저장 로직 실행
        saveProfileData();
    }
}

// 4. 수정한 프로필 데이터 서버에 저장
async function saveProfileData() {
    const formData = {
        name: document.querySelector('input[name="name"]').value,
        email: document.querySelector('input[name="email"]').value,
        phone: document.querySelector('input[name="phone"]').value,
        birth: document.querySelector('input[name="birth"]').value,
        nickname: document.querySelector('input[name="nickname"]').value
    };

    try {
        const response = await authFetch('/api/user/profile/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
			credentials: 'include',
            body: JSON.stringify(formData)
        });

        if (response.ok) {
            alert('정보가 성공적으로 수정되었습니다.');
            location.reload();
        } else {
            const errorMsg = await response.text();
            alert("수정 실패: " + errorMsg);
        }
    } catch (error) {
        console.error("저장 중 오류 발생 : ", error);
    }
}

// 5. 비밀번호 변경 모달 관련
function openchangePW() {
    document.getElementById('pwModal').classList.remove('hidden');
}

function closePWModal() {
    document.getElementById('pwModal').classList.add('hidden');
    document.getElementById('pwchangeform').reset();
}
/**
 * KKIRI - 모임 초대장 스크립트
 * DB의 초대 코드와 연동하여 링크 및 QR 생성
 */

let timerInterval;

document.addEventListener('DOMContentLoaded', function() {
    // 페이지 로드 시 즉시 실행
    initInvitePage();
    // 타이머 시작 (3분)
    startTimer(180);
});

/**
 * 페이지 초기화: HTML에 심어진 DB 데이터 추출 및 UI 반영
 */
async function initInvitePage() {
    // 1. 데이터가 숨겨져 있는 'main' 태그를 타겟으로 잡습니다.
    // HTML 파일에서 <main th:if="..." id="main-container" ...> 로 되어 있어야 합니다.
    const mainContainer = document.querySelector('main[th\\:data-invite-code]') || document.getElementById('main-container') || document.querySelector('main');
    const groupNameDisplay = document.getElementById('display-group-name');
    const inviteLinkDisplay = document.getElementById('invite-link-text');

    if (!mainContainer) {
        console.error("데이터를 포함한 main 컨테이너를 찾을 수 없습니다.");
        return;
    }

    // 2. 데이터 속성 읽기 (카멜케이스 dataset과 일반 getAttribute 모두 대응)
    const dbInviteCode = mainContainer.getAttribute('data-invite-code') || mainContainer.dataset.inviteCode;
    const dbPurpose = mainContainer.getAttribute('data-purpose') || mainContainer.dataset.purpose || "기본";

    console.log("DB에서 읽어온 초대 코드:", dbInviteCode);

    // 3. 유효성 검사 및 'UNKNOWN' 방지
    if (!dbInviteCode || dbInviteCode === "UNKNOWN" || dbInviteCode === "null") {
        console.warn("초대 코드가 정상적으로 로드되지 않았습니다.");
        if (inviteLinkDisplay) inviteLinkDisplay.innerText = "코드를 불러오는 중...";
        // 실제 운영 시에는 여기서 return 하지 않고 잠시 기다리거나 재시도 로직을 넣을 수 있습니다.
    }

    // 4. 배경 이미지 변경 (DB 카테고리 기준)
    updateHeroImage(dbPurpose);

    // 5. 실제 초대 접속 링크 생성
    const ngrokBaseUrl = "https://dorothy-untumultuous-hygrometrically.ngrok-free.dev";
    const fullLink = `${ngrokBaseUrl}/join?code=${dbInviteCode}`;

    // 6. UI 반영 및 복사용 데이터 저장
    document.body.dataset.inviteUrl = fullLink;
    
    if (inviteLinkDisplay && dbInviteCode) {
        inviteLinkDisplay.innerText = dbInviteCode; // 화면에는 'EF0ECAAC' 같은 코드만 표시
    }
    
    // 7. QR 코드 생성
    updateQRCode(fullLink);
}

/**
 * 카테고리(purpose)별 배경 이미지 업데이트
 */
function updateHeroImage(purpose) {
    const bgElement = document.getElementById('invitation-image');
    if (!bgElement) return;

    const images = {
        '여행': 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=600',
        '취미': 'https://images.unsplash.com/photo-1517404212738-19266ad94814?q=80&w=600',
        '데이트': 'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?q=80&w=600',
        '동호회': 'https://images.unsplash.com/photo-1521737604893-d14cc237f11d?q=80&w=600'
    };
    
    // DB의 CATEGORY 값이 위 키값과 일치하지 않을 경우를 대비한 기본 이미지
    const bgUrl = images[purpose] || 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=600';
    bgElement.style.backgroundImage = `url("${bgUrl}")`;
}

/**
 * QR 코드 업데이트
 */
function updateQRCode(link) {
    const qrImage = document.getElementById('qr-image');
    if (qrImage) {
        qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(link)}`;
        qrImage.classList.remove('grayscale', 'blur-sm', 'opacity-30');
    }
}

/**
 * 초대 링크 복사
 */
async function copyLink() {
    const fullLink = document.body.dataset.inviteUrl;
    if (!fullLink || fullLink.includes('undefined')) {
        alert("링크가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.");
        return;
    }

    try {
        await navigator.clipboard.writeText(fullLink);
        alert("친구에게 보낼 초대 링크가 복사되었습니다! 🚀");
    } catch (err) {
        const textArea = document.createElement("textarea");
        textArea.value = fullLink;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        alert("링크가 복사되었습니다.");
    }
}

/**
 * 타이머
 */
function startTimer(duration) {
    const display = document.querySelector('#timer');
    const wrapper = document.querySelector('#timer-wrapper');
    const qrImage = document.getElementById('qr-image');
    const qrStatus = document.getElementById('qr-status');
    
    if (!display) return;

    let timer = duration;
    clearInterval(timerInterval);

    timerInterval = setInterval(() => {
        let min = Math.floor(timer / 60);
        let sec = timer % 60;
        display.textContent = `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;

        if (--timer < 0) {
            clearInterval(timerInterval);
            display.textContent = "만료됨";
            if (wrapper) wrapper.classList.replace('text-primary', 'text-red-500');
            if (qrImage) qrImage.classList.add('grayscale', 'blur-sm', 'opacity-30');
            if (qrStatus) qrStatus.innerText = "초대 코드가 만료되었습니다. 다시 시도하세요.";
        }
    }, 1000);
}

/**
 * 새로고침 버튼 클릭 시
 */
function resetTimer() {
    clearInterval(timerInterval);
    startTimer(180);
    initInvitePage(); // 다시 데이터를 읽어와서 UI 갱신
    alert("초대 코드가 갱신되었습니다.");
}

/**
 * 이미지로 저장
 */
function saveImage() {
    const target = document.getElementById('capture-area');
    if (!target) return;

    html2canvas(target, { 
        useCORS: true, 
        scale: 2,
        backgroundColor: "#ffffff" 
    }).then(canvas => {
        const link = document.createElement('a');
        link.download = `KKIRI_초대장_${new Date().getTime()}.png`;
        link.href = canvas.toDataURL();
        link.click();
    });
}
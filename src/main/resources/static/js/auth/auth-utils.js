// auth-utils.js 수정본

function requestCertification() {
    const IMP = window.IMP;
    IMP.init("imp76125415");

    IMP.certification({
        pg: "inicis_unified",
        merchant_uid: `moim_cert_${new Date().getTime()}`,
        popup: true
    }, async (rsp) => {
        if (rsp.success) {
            try {
                const response = await fetch('/api/auth/verify-cert', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ imp_uid: rsp.imp_uid })
                });

                if (response.ok) {
                    const certData = await response.json();
                    
                    verifiedCi = certData.ci;   // ✅ window. 제거
                    isCertified = true;          // ✅ 중복 제거
                    
                    sessionStorage.setItem('certUid', rsp.imp_uid);
                    alert("본인인증이 완료되었습니다.");
                    
                    updateCertStatus(certData);  // ✅ 주석 해제
                }
            } catch (error) {
                alert("인증 정보 확인 중 서버 오류가 발생했습니다.");
            }
        } else {
            alert(`본인인증 실패: ${rsp.error_msg}`);
        }
    });
}

function updateCertStatus(data) {
    const certBtn = document.getElementById('cert-btn');
    if (!certBtn) return;

    // ✅ fields id를 실제 HTML id인 'phoneNumber'로 통일
    const fields = ['name', 'birth', 'phoneNumber'];

    certBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg> 인증 완료`;
    certBtn.classList.remove('bg-primary', 'shadow-primary/20');
    certBtn.classList.add('bg-green-500', 'shadow-green-500/20', 'cursor-default');
    certBtn.onclick = null;

    fields.forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            // ✅ 값 채우기 + 스타일 변경 둘 다
            input.value = data[id === 'phoneNumber' ? 'phone' : id] || '';
            input.readOnly = true;
            input.classList.remove('bg-slate-100', 'text-slate-400');
            input.classList.add('bg-slate-50', 'text-slate-900', 'font-bold');
        }
    });
}
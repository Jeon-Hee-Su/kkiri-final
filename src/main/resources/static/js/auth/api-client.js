/**
 * api-client.js: 모든 API 요청 시 토큰을 자동으로 포함하는 공통 함수
 */
async function fetchWithAuth(url, options = {}) {

    // 3. 기존 options와 합치기
    const fetchOptions = {
        ...options,
        headers: {
            ...authHeaders,
            ...options.headers
        },
        credentials: 'include' //브라우저가 인증 쿠키를 자동으로 서버에 전송
    };

    const response = await fetch(url, fetchOptions);

    // 4. 공통 에러 처리 (401 Unauthorized 등)
    if (response.status === 401) {
        console.warn("인증 만료됨. 로그인이 필요합니다.");
        // 필요 시 여기서 로그인 페이지로 리다이렉트 로직 추가 가능
    }

    return response;
}
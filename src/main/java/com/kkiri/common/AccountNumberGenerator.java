package com.kkiri.common;

import java.util.Random;

/**
 * 은행별 실제 계좌번호 형식에 맞춰 랜덤 계좌번호를 생성하는 유틸 클래스
 * '#' 하나당 0~9 숫자 1자리로 치환됩니다.
 * 고정 숫자(ex: 110, 1002 등)는 패턴에 직접 포함되어 그대로 유지됩니다.
 */
public class AccountNumberGenerator {

    private static final Random RANDOM = new Random();

    /**
     * 은행 코드에 맞는 계좌번호 생성
     *
     * @param bankCode BANKS 테이블의 BANK_CODE 값
     * @return 패턴에 맞게 생성된 계좌번호 문자열
     *
     * [ 은행별 형식 ]
     *  088 신한은행  : 110-###-######        → 110 고정 + 3자리 + 6자리
     *  004 국민은행  : ####02-##-######       → 4자리 + 02 고정 + 2자리 + 6자리
     *  020 우리은행  : 1002-###-######        → 1002 고정 + 3자리 + 6자리
     *  003 기업은행  : ###-######-##-###      → 3자리 + 6자리 + 2자리 + 3자리
     *  090 카카오뱅크: 3333-##-#######        → 3333 고정 + 2자리 + 7자리
     *  011 농협은행  : 301-####-####-##       → 301 고정 + 4자리 + 4자리 + 2자리
     *  092 토스뱅크  : 1000-####-####         → 1000 고정 + 4자리 + 4자리
     */
    public static String generate(String bankCode) {
        switch (bankCode) {
            case "088": return applyPattern("110-###-######");       // 신한은행
            case "004": return applyPattern("####02-##-######");     // KB국민은행
            case "020": return applyPattern("1002-###-######");      // 우리은행
            case "003": return applyPattern("###-######-##-###");    // IBK기업은행
            case "090": return applyPattern("3333-##-#######");      // 카카오뱅크
            case "011": return applyPattern("301-####-####-##");     // NH농협은행
            case "092": return applyPattern("1000-####-####");       // 토스뱅크
            default:    return applyPattern("############");          // 기본 12자리
        }
    }

    /**
     * 패턴 문자열에서 '#'을 랜덤 숫자(0~9)로 치환
     * 숫자와 '-'는 그대로 유지됩니다.
     *
     * @param pattern ex) "110-###-######"
     * @return ex) "110-042-378291"
     */
    private static String applyPattern(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '#') {
                sb.append(RANDOM.nextInt(10)); // 0~9 랜덤 숫자
            } else {
                sb.append(c);                  // 고정 숫자, '-' 구분자는 그대로
            }
        }
        return sb.toString();
    }
}
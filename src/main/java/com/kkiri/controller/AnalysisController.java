package com.kkiri.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kkiri.model.dto.AnalysisResponse;
import com.kkiri.model.vo.GroupVO;
import com.kkiri.model.vo.UserVO;
import com.kkiri.service.AnalysisService;
import com.kkiri.service.GroupService;
import com.kkiri.service.UserService;

/**
 * [수정] groupId를 쿼리 파라미터로 직접 받아 해당 그룹의 AI 분석을 수행
 * groupId 미전달 시 로그인 유저의 첫 번째 그룹으로 자동 폴백
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @GetMapping("/analysis")
    public ResponseEntity<AnalysisResponse> getAnalysis(
            @RequestParam(value = "groupId", required = false) Long groupId,
            Authentication authentication) {

        // 1. JWT 인증 체크
        if (authentication == null || !authentication.isAuthenticated()) {
            System.err.println("❌ [AnalysisController] 로그인이 필요합니다.");
            return ResponseEntity.status(401).build();
        }

        // 2. groupId가 전달되지 않았으면 유저의 첫 번째 그룹으로 자동 설정
        if (groupId == null || groupId <= 0) {
            UserVO user = userService.findByEmail(authentication.getName());
            if (user == null) {
                return ResponseEntity.status(404).build();
            }
            List<GroupVO> groups = groupService.findGroupsByUserId(user.getUserId());
            if (groups == null || groups.isEmpty()) {
                System.err.println("❌ [AnalysisController] 소속 그룹 없음");
                return ResponseEntity.status(404).build();
            }
            groupId = (long) groups.get(0).getGroupId();
        }

        // 3. 해당 그룹의 멤버인지 검증
        UserVO user = userService.findByEmail(authentication.getName());
        if (user != null && !groupService.isUserInGroup(groupId.intValue(), user.getUserId())) {
            System.err.println("❌ [AnalysisController] 해당 그룹의 멤버가 아닙니다.");
            return ResponseEntity.status(403).build();
        }

        System.out.println("✅ [AnalysisController] 그룹 ID " + groupId + "번 분석 시작");

        // 4. AI 분석 실행
        AnalysisResponse report = analysisService.getAiAnalysis(groupId);

        if (report != null) {
            return ResponseEntity.ok(report);
        } else {
            return ResponseEntity.status(500).build();
        }
    }
}

package com.kkiri.interceptor;

import com.kkiri.service.GroupService;
import com.kkiri.service.UserService;
import com.kkiri.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupAccessInterceptor implements HandlerInterceptor {

    private final GroupService groupService;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. 로그인 여부 확인
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            response.sendRedirect("/login");
            return false;
        }

        // 2. groupId 추출 (PathVariable or QueryParam 둘 다 처리)
        String path = request.getRequestURI(); // ex) /group/detail/61
        Integer groupId = null;

        // /group/detail/{groupId} 형태
        if (path.matches("/group/detail/\\d+")) {
            String[] parts = path.split("/");
            groupId = Integer.parseInt(parts[parts.length - 1]);
        }

        // ?groupId=61 형태 (/groupsettings, /groupmembers, /membermanage)
        if (groupId == null) {
            String param = request.getParameter("groupId");
            if (param != null && !param.isEmpty()) {
                try { groupId = Integer.parseInt(param); } catch (NumberFormatException ignored) {}
            }
        }

        // groupId가 없거나 0이면 통과 (서버에서 첫 그룹으로 redirect하는 케이스)
        if (groupId == null || groupId == 0) return true;

        // 3. 유저 정보 조회
        UserVO user = userService.findByEmail(auth.getName());
        if (user == null) {
            response.sendRedirect("/login");
            return false;
        }

        // 4. 그룹 멤버 여부 확인
        boolean isMember = groupService.isUserInGroup(groupId, user.getUserId());
        if (!isMember) {
            log.warn("비멤버 접근 차단 - userId: {}, groupId: {}, path: {}",
                    user.getUserId(), groupId, path);
            response.sendRedirect("/");
            return false;
        }

        return true;
    }
}
package com.kkiri.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    public String executeOcr(String imagePath) {
        StringBuilder output = new StringBuilder();

        try {
            String userName = System.getProperty("user.name");
            String projectPath = System.getProperty("user.dir");

            // --- [1. 파이썬 실행 경로 유연화: kkiri 가상환경 우선] ---
            String[] pythonPathCandidates = {
                // 1순위: 유저님의 아나콘다 'kkiri' 가상환경 경로 (가장 확실함)
                "C:\\Users\\" + userName + "\\anaconda3\\envs\\kkiri\\python.exe",
                // 2순위: 환경변수에 설정된 python (보통 base 환경일 확률 높음)
                "python",
                // 3순위: 시스템 전체 설치 경로의 가상환경
                "C:\\ProgramData\\anaconda3\\envs\\kkiri\\python.exe"
            };

            String pythonPath = "";
            for (String path : pythonPathCandidates) {
                try {
                    // 해당 경로의 파이썬이 존재하고 정상 작동하는지 체크
                    Process check = new ProcessBuilder(path, "--version").start();
                    if (check.waitFor() == 0) {
                        pythonPath = path;
                        break;
                    }
                } catch (Exception e) {
                    continue; // 경로가 없으면 다음 후보로
                }
            }

            if (pythonPath.isEmpty()) {
                return "에러: 'kkiri' 가상환경을 찾을 수 없습니다. 아나콘다 설치 경로를 확인해주세요.";
            }
            // -------------------------------------------------------

            // --- [2. 최신 버전 스크립트 탐색] ---
            String scriptDir = projectPath + "\\src\\main\\resources\\python";
            File dir = new File(scriptDir);
            File[] files = dir.listFiles((d, name) -> 
                name.startsWith("ocr_analysis_v") && name.endsWith(".py")
            );

            String scriptPath;
            if (files != null && files.length > 0) {
                Arrays.sort(files);
                scriptPath = files[files.length - 1].getAbsolutePath();
            } else {
                scriptPath = scriptDir + "\\ocr_analysis.py";
            }

            System.out.println("사용 중인 Python: " + pythonPath);
            System.out.println("실행 스크립트: " + scriptPath);

            // 3. ProcessBuilder 설정
            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath, imagePath);
            pb.redirectErrorStream(true);

            // 4. 프로세스 시작
            Process process = pb.start();

            // 5. 결과 읽기 (UTF-8 설정 중요)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                System.out.println("Python Log: " + line);
                
                // JSON 형태의 결과값만 추출
                if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                    output.append(trimmedLine);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "에러 발생: 종료 코드 " + exitCode;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "자바 실행 오류: " + e.getMessage();
        }

        return output.toString();
    }
}
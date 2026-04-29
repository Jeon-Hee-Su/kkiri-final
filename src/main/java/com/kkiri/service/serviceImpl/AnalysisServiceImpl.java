package com.kkiri.service.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkiri.model.dto.AnalysisResponse;
import com.kkiri.service.AnalysisService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    /**
     * 환경에 따라 python 실행 파일 경로를 자동으로 탐색합니다.
     * 우선순위: 가상환경(venv) > python3 > python
     */
    private String resolvePythonPath(String projectPath) {
        // 1. 프로젝트 루트의 venv/Scripts/python.exe (Windows)
        File winVenv = new File(projectPath + File.separator + "venv" + File.separator + "Scripts" + File.separator + "python.exe");
        if (winVenv.exists()) return winVenv.getAbsolutePath();

        // 2. 프로젝트 루트의 venv/bin/python (Linux/Mac)
        File unixVenv = new File(projectPath + File.separator + "venv" + File.separator + "bin" + File.separator + "python");
        if (unixVenv.exists()) return unixVenv.getAbsolutePath();

        // 3. python3 명령어 (Linux/Mac 기본)
        try {
            Process p = new ProcessBuilder("python3", "--version").start();
            if (p.waitFor() == 0) return "python3";
        } catch (Exception ignored) {}

        // 4. python 명령어 (Windows 기본 / fallback)
        return "python";
    }

    @Override
    public AnalysisResponse getAiAnalysis(Long groupId) {
        try {
            String projectPath = System.getProperty("user.dir");
            String pythonPath  = resolvePythonPath(projectPath);

            // =====================================================
            // 파이썬 스크립트 디렉토리 탐색
            // 우선순위 1: Docker 환경  → /app/python
            // 우선순위 2: 로컬 개발 환경 → {user.dir}/src/main/resources/python
            // =====================================================
            String[] candidatePaths = {
                projectPath + File.separator + "python",                          // Docker: /app/python
                projectPath + File.separator + "src" + File.separator + "main"   // Local
                        + File.separator + "resources" + File.separator + "python"
            };

            File pythonDir = null;
            for (String candidate : candidatePaths) {
                File dir = new File(candidate);
                if (dir.exists() && dir.isDirectory()) {
                    pythonDir = dir;
                    System.out.println("✅ [Analysis] 파이썬 스크립트 디렉토리 발견: " + candidate);
                    break;
                }
            }

            if (pythonDir == null) {
                System.err.println("❌ [Analysis] 파이썬 스크립트 디렉토리를 찾을 수 없습니다.");
                System.err.println("   시도한 경로들: " + java.util.Arrays.toString(candidatePaths));
                return null;
            }

            File[] files = pythonDir.listFiles((dir, name) -> name.startsWith("analysis_v") && name.endsWith(".py"));

            if (files == null || files.length == 0) {
                System.err.println("❌ [Analysis] analysis_v*.py 파일이 없습니다. 경로: " + pythonDir.getAbsolutePath());
                return null;
            }

            java.util.Arrays.sort(files);
            String scriptPath = files[files.length - 1].getAbsolutePath();
            System.out.println("🚀 [Analysis] Python: " + pythonPath + " / Script: " + files[files.length - 1].getName());

            String resultJsonPath = projectPath + File.separator + "result_group_" + groupId + ".json";

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath,
                    scriptPath,
                    String.valueOf(groupId),
                    resultJsonPath
            );
            pb.directory(new File(projectPath));
            // Python 환경변수 유지 (가상환경 PATH 포함)
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // stdout + stderr 동시 읽기 (deadlock 방지)
            StringBuilder stdOut = new StringBuilder();
            StringBuilder stdErr = new StringBuilder();

            Thread outThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("🐍 Python: " + line);
                        stdOut.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            Thread errThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.err.println("🐍 Python Error: " + line);
                        stdErr.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            outThread.start();
            errThread.start();

            int exitCode = process.waitFor();
            outThread.join();
            errThread.join();

            if (exitCode == 0) {
                File jsonFile = new File(resultJsonPath);
                if (!jsonFile.exists()) {
                    System.err.println("❌ [Analysis] result JSON 파일이 생성되지 않았습니다: " + resultJsonPath);
                    return null;
                }
                ObjectMapper mapper = new ObjectMapper();
                AnalysisResponse result = mapper.readValue(jsonFile, AnalysisResponse.class);
                jsonFile.delete();
                return result;
            } else {
                System.err.println("❌ [Analysis] 파이썬 종료 코드: " + exitCode);
                System.err.println("   stderr: " + stdErr.toString().trim());
                System.err.println("   stdout: " + stdOut.toString().trim());
            }

        } catch (Exception e) {
            System.err.println("❌ [Analysis] 예외 발생: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
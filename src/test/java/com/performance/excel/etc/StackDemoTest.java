package com.performance.excel.etc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootTest
@ActiveProfiles("test")
public class StackDemoTest {

    @Test
    void test() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("====== 동기 호출 스택 ======");
        testRecursive(1);
        System.out.println("====== 비동기 호출 스택 ======");
        CompletableFuture<Void> finalFuture = testAsync(1);

        // 5분 타임아웃
        // 이제 finalFuture는 모든 체이닝된 작업의 최종 완료를 나타냅니다.
        finalFuture.get(5, TimeUnit.MINUTES);
    }

    void testRecursive(int depth) {
        System.out.println("=== depth " + depth + " 동기 호출 스택 ===");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        Arrays.stream(stack).forEach(System.out::println);
        System.out.println("Depth: " + depth +
                " / Thread: " + Thread.currentThread().getName() +
                " / Stack depth: " + stack.length);

        if (depth < 5) {
            testRecursive(depth + 1);
        }
    }

    /**
     * 각 깊이의 비동기 작업과 그 하위 작업들의 완료를 나타내는 CompletableFuture를 반환합니다.
     * @param depth 현재 깊이
     * @return 모든 연관 작업의 완료를 나타내는 CompletableFuture
     */
    CompletableFuture<Void> testAsync(int depth) {
        // 1. 현재 깊이의 작업을 비동기로 실행
        return CompletableFuture.runAsync(() -> {
            System.out.println("=== depth " + depth + " 비동기 호출 스택 ===");
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            Arrays.stream(stack).forEach(System.out::println);
            System.out.println("Depth: " + depth +
                    " / Thread: " + Thread.currentThread().getName() +
                    " / Stack depth: " + stack.length);

            try {
                // 디버거가 스택을 관찰할 시간을 확보하기 위한 지연
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).thenComposeAsync(v -> { // 2. 위 작업이 끝나면 이어서 다음 작업을 실행
            if (depth < 5) {
                // 3. 재귀적으로 다음 깊이의 비동기 작업을 호출하고, 그 결과를 체인에 연결
                return testAsync(depth + 1);
            } else {
                // 4. 마지막 깊이면, 완료된 Future를 반환하여 체인을 종료
                return CompletableFuture.completedFuture(null);
            }
        });
    }
}


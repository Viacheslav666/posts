package ru.posts.emulator.vk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.posts.emulator.vk.VkApiController.ExecuteCall;

class VkExecuteParseTest {

    @Test
    void parsesFlatApiCalls() {
        List<ExecuteCall> calls = VkApiController.parseExecuteCalls(
                "return [API.wall.deleteComment({owner_id: -1, comment_id: 1}),"
                        + " API.wall.deleteComment({owner_id: -1, comment_id: 2})];",
                25);
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).method()).isEqualTo("wall.deleteComment");
        assertThat(calls.get(0).argsJson()).contains("comment_id: 1");
        assertThat(calls.get(1).argsJson()).contains("comment_id: 2");
    }

    @Test
    void parsesEmptyArgs() {
        List<ExecuteCall> calls = VkApiController.parseExecuteCalls("API.groups.getBanned();", 5);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).method()).isEqualTo("groups.getBanned");
        assertThat(calls.get(0).argsJson()).isEmpty();
    }

    @Test
    void pathologicalInputFinishesQuickly() {
        // Former regex ReDoS shape: many "API.." prefixes without valid method bodies.
        String poison = "API.".repeat(50_000) + "{{";
        long started = System.nanoTime();
        List<ExecuteCall> calls = VkApiController.parseExecuteCalls(poison, 25);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertThat(calls).isEmpty();
        assertThat(elapsedMs).isLessThan(2_000L);
    }
}

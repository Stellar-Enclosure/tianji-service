package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessDependencyBoundaryTest {
    @Test
    void processHostContainsFlowableAndExcludesTheAgentAndShardingRuntime() throws Exception {
        String classpath = Files.readString(Path.of("target", "process-runtime-classpath.txt")).strip();
        String[] paths = classpath.split(Pattern.quote(File.pathSeparator));
        URL[] entries = new URL[paths.length];
        for (int index = 0; index < paths.length; index++) {
            entries[index] = Path.of(paths[index]).toUri().toURL();
        }
        // Surefire includes the Agent Server for cross-module acceptance fixtures.
        try (var runtime = new URLClassLoader(entries, ClassLoader.getPlatformClassLoader())) {
            assertNotNull(runtime.getResource("org/flowable/engine/ProcessEngine.class"));
            assertNotNull(runtime.getResource("org/flowable/cmmn/engine/CmmnEngine.class"));
            assertNotNull(runtime.getResource("org/flowable/dmn/engine/DmnEngine.class"));
            assertNull(runtime.getResource("top/egon/cola/platform/tianji/agent/server/TianjiAgentServerApplication.class"));
            assertNull(runtime.getResource("com/google/adk/agents/BaseAgent.class"));
            assertNull(runtime.getResource("org/springframework/ai/chat/model/ChatModel.class"));
            assertNull(runtime.getResource("org/apache/shardingsphere/driver/ShardingSphereDriver.class"));
        }
    }
}

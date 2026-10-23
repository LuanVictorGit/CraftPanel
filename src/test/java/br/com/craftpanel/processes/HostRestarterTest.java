package br.com.craftpanel.processes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class HostRestarterTest {

    @Test
    void testJarLaunchIsRebuiltEvenWithSpacesInThePath(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("my server"));
        Files.createFile(tempDir.resolve("my server").resolve("paper 26.2.jar"));

        List<String> arguments = HostRestarter.rebuildArguments(List.of("-Xmx2G", "-Dfile.encoding=UTF-8"),
                "my server/paper 26.2.jar", "my server/paper 26.2.jar --nogui", tempDir.toFile());

        assertEquals(List.of("-Xmx2G", "-Dfile.encoding=UTF-8", "-jar", "my server/paper 26.2.jar", "--nogui"), arguments);
    }

    @Test
    void testMainClassLaunchKeepsTheClassPath(@TempDir Path tempDir) {
        String classPath = "a.jar" + File.pathSeparator + "b.jar";

        List<String> arguments = HostRestarter.rebuildArguments(List.of("-Xmx1G"), classPath, "org.example.Main --port 25566", tempDir.toFile());

        assertEquals(List.of("-Xmx1G", "-cp", classPath, "org.example.Main", "--port", "25566"), arguments);
    }

    @Test
    void testOptionsFromEnvironmentVariablesAreNotRepeated() {
        List<String> arguments = HostRestarter.withoutEnvironmentOptions(List.of("-javaagent:agent.jar", "-Xmx2G", "-Dx=1"),
                null, " -javaagent:agent.jar ", "");

        assertEquals(List.of("-Xmx2G", "-Dx=1"), arguments);
    }

    @Test
    void testArgumentFileReachesJavaUnchanged(@TempDir Path tempDir) throws Exception {
        List<String> tricky = List.of("C:\\Program Files\\server dir\\", "say \"hi\"", "back\\slash", "#not-a-comment",
                "it's", "tab\there", "%PATH%", "a&b|c<d>e^f");
        String echoClassPath = Paths.get(ArgEcho.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> arguments = new ArrayList<>(List.of("-cp", echoClassPath, ArgEcho.class.getName()));
        arguments.addAll(tricky);
        Path argFile = tempDir.resolve("restart-args.txt");
        HostRestarter.writeArgFile(argFile, arguments);

        String java = ProcessHandle.current().info().command().orElseThrow();
        Process process = new ProcessBuilder(java, "@" + argFile).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));

        assertEquals(0, process.exitValue(), output);
        assertEquals(tricky, List.of(output.split("\u0000", -1)).subList(0, tricky.size()));
    }
}

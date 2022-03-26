import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Java 16+</b>
 * <pre>{@code java Update.java [revision]}</pre>
 * Where {@code revision} is a <a href="https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator">VMA</a>
 * branch name or commit hash. Default = {@code master}
 */
public class Update {

    private static String httpGet(String url, String errorMessage) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        if (connection.getResponseCode() != 200) {
            throw new Error(errorMessage + ": " + connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return new String(connection.getInputStream().readAllBytes());
    }

    private static String findRegex(String text, String regex, String errorMessage) {
        return Pattern.compile(regex).matcher(text).results().findFirst()
                .orElseThrow(() -> new Error(errorMessage)).group(1);
    }

    private static String findVulkanVersion(String text) {
        Matcher m = Pattern.compile("VK_VERSION_(\\d+)_(\\d+)").matcher(text);
        int major = 0, minor = 0;
        while (m.find()) {
            int mj = Integer.parseInt(m.group(1)), mn = Integer.parseInt(m.group(2));
            if (mj > major || (mj == major && mn > minor)) {
                major = mj;
                minor = mn;
            }
        }
        if (major == 0 && minor == 0) throw new Error("Cannot detect Vulkan version");
        return major + "." + minor;
    }

    private static String replaceReadme(String text, String... replacements) {
        for (int i = 0; i < replacements.length / 2; i++) {
            String key = replacements[i * 2], value = replacements[i * 2 + 1];
            text = text.replaceAll("<!--" + key + "-->.*?<!--/" + key + "-->", "<!--" + key + "-->" + value + "<!--/" + key + "-->");
        }
        return text;
    }

    public static void main(String[] args) throws Exception {
        String revision = args.length > 0 ? args[0] : "master";
        System.out.println("Updating VMA revision... " + revision);

        String sha = findRegex(httpGet("https://api.github.com/repos/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/commits?per_page=1&sha=" + revision,
                "Failed to get commit hash"), "\"sha\"\\s*:\\s*\"(.+?)\"", "Cannot extract commit hash");
        System.out.println("Commit hash: " + sha);

        String content = httpGet("https://raw.githubusercontent.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/" + sha + "/include/vk_mem_alloc.h",
                "Failed to download vk_mem_alloc.h");
        String version = findRegex(content, "<b>Version\\s*(.+?)\\s*</b>", "Cannot extract version");
        String vk = findVulkanVersion(content);
        System.out.println("VMA version: " + version);
        System.out.println("Vulkan version: " + vk);
        Files.writeString(Path.of("include/vk_mem_alloc.h"), content);

        Path readmePath = Path.of("README.md");
        Files.writeString(readmePath, replaceReadme(Files.readString(readmePath),
                "VER", version,
                "VK", vk,
                "REV", "[" + sha + "](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/tree/" + sha + ") "));

        System.out.println("Generating C++ bindings...");
        ProcessBuilder generator = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "Generate.java");
        generator.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        generator.redirectError(ProcessBuilder.Redirect.INHERIT);
        System.exit(generator.start().waitFor());
    }
}

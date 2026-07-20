import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ThreadDumpAnalyzer {
	private static final List<String> REDIS_KEYWORDS = List.of(
		"org.springframework.data.redis",
		"io.lettuce",
		"redis.clients",
		"CompletableFuture",
		"LockSupport.park"
	);

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("Usage: java ThreadDumpAnalyzer <thread-dump.txt>");
			return;
		}

		String content = Files.readString(Path.of(args[0]));
		List<String> blocks = splitThreadBlocks(content);

		int tomcatTotal = 0;
		int tomcatWaiting = 0;
		int redisRelated = 0;
		int redisWaiting = 0;

		for (String block : blocks) {
			String firstLine = firstLine(block);

			if (!isTomcatWorker(firstLine)) {
				continue;
			}

			tomcatTotal++;

			boolean waiting = block.contains("java.lang.Thread.State: WAITING")
				|| block.contains("java.lang.Thread.State: TIMED_WAITING");

			boolean redis = REDIS_KEYWORDS.stream()
				.anyMatch(block::contains);

			if (waiting) {
				tomcatWaiting++;
			}

			if (redis) {
				redisRelated++;
			}

			if (waiting && redis) {
				redisWaiting++;
			}
		}

		System.out.println("Tomcat worker threads        : " + tomcatTotal);
		System.out.println("Tomcat WAITING/TIMED_WAITING : " + tomcatWaiting);
		System.out.println("Tomcat Redis-related         : " + redisRelated);
		System.out.println("Tomcat Redis waiting         : " + redisWaiting);
	}

	private static List<String> splitThreadBlocks(String content) {
		List<String> blocks = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		String[] lines = content.split("\\R");

		for (String line : lines) {
			if (line.startsWith("\"") && current.length() > 0) {
				blocks.add(current.toString());
				current.setLength(0);
			}

			current.append(line).append(System.lineSeparator());
		}

		if (current.length() > 0) {
			blocks.add(current.toString());
		}

		return blocks;
	}

	private static String firstLine(String block) {
		int idx = block.indexOf(System.lineSeparator());
		if (idx == -1) {
			return block;
		}
		return block.substring(0, idx);
	}

	private static boolean isTomcatWorker(String firstLine) {
		return firstLine.contains("http-nio-")
			&& firstLine.contains("-exec-");
	}
}

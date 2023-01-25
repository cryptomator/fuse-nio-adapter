package org.cryptomator.frontend.fuse.mount;

import org.jetbrains.annotations.Blocking;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

class ProcessHelper {

	private ProcessHelper() {
	}

	/**
	 * Waits {@code timeoutSeconds} seconds for {@code process} to finish with exit code {@code 0}.
	 *
	 * @param process        The process to wait for
	 * @param timeoutSeconds How long to wait (in seconds)
	 * @param cmdDescription A short description of the process used to generate log and exception messages
	 * @throws TimeoutException       Thrown when the process doesn't finish in time
	 * @throws InterruptedException   Thrown when the thread is interrupted while waiting for the process to finish
	 * @throws CommandFailedException Thrown when the process exit code is non-zero
	 */
	@Blocking
	static void waitForSuccess(Process process, int timeoutSeconds, String cmdDescription) throws TimeoutException, InterruptedException, CommandFailedException {
		boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!exited) {
			throw new TimeoutException(cmdDescription + " timed out after " + timeoutSeconds + "s");
		}
		if (process.exitValue() != 0) {
			@SuppressWarnings("resource") var stdout = process.inputReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining("\n"));
			@SuppressWarnings("resource") var stderr = process.errorReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining("\n"));
			throw new CommandFailedException(cmdDescription, process.exitValue(), stdout, stderr);
		}
	}

	static class CommandFailedException extends Exception {

		int exitCode;
		String stdout;
		String stderr;

		private CommandFailedException(String cmdDescription, int exitCode, String stdout, String stderr) {
			super(cmdDescription + " returned with non-zero exit code " + exitCode);
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}

	}
}

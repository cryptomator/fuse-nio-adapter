package org.cryptomator.frontend.fuse.mount;

import org.jetbrains.annotations.Blocking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

class ProcessHelper {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessHelper.class);

	private ProcessHelper() {
	}

	/**
	 * Waits {@code timeoutSeconds} seconds for {@code process} to finish with exit code {@code 0}.
	 *
	 * @param process          The process to wait for
	 * @param timeoutSeconds   How long to wait (in seconds)
	 * @param cmdDescription   A short description of the process used to generate log and exception messages
	 * @param exceptionFactory A function constructing a new exception with the given error message.
	 * @param <E>              What kind of exception to throw if the process exits with a non-zero code
	 * @throws TimeoutException     Thrown when the process doesn't finish in time
	 * @throws InterruptedException Thrown when the thread is interrupted while waiting for the process to finish
	 * @throws E                    Thrown when the exit code is non-zero
	 */
	@Blocking
	static <E extends Exception> void waitForSuccess(Process process, int timeoutSeconds, String cmdDescription, Function<String, E> exceptionFactory) throws TimeoutException, InterruptedException, E {
		boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!exited) {
			throw new TimeoutException(cmdDescription + " timed out after " + timeoutSeconds + "s");
		}
		if (process.exitValue() != 0) {
			@SuppressWarnings("resource") var stdout = process.inputReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining("\n"));
			@SuppressWarnings("resource") var stderr = process.errorReader(StandardCharsets.UTF_8).lines().collect(Collectors.joining("\n"));
			LOG.warn("{} failed with exit code {}:\nSTDOUT: {}\nSTDERR: {}\n", cmdDescription, process.exitValue(), stdout, stderr);
			throw exceptionFactory.apply(cmdDescription + " failed with exit code" + process.exitValue());
		}
	}
}

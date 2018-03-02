package org.cryptomator.frontend.fuse.mount;

import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

class ProcessUtil {

	/**
	 * Fails with a CommandFailedException, if the process did not finish with the expected exit code.
	 * 
	 * @param proc A finished process
	 * @param expectedExitValue Exit code returned by the process
	 * @throws CommandFailedException Thrown in case of unexpected exit values
	 */
	public static void assertExitValue(Process proc, int expectedExitValue) throws CommandFailedException {
		int actualExitValue = proc.exitValue();
		if (actualExitValue != expectedExitValue) {
			try {
				String error = toString(proc.getErrorStream(), StandardCharsets.UTF_8);
				throw new CommandFailedException("Command failed with exit code " + actualExitValue + ". Expected " + expectedExitValue + ". Stderr: " + error);
			} catch (IOException e) {
				throw new CommandFailedException("Command failed with exit code " + actualExitValue + ". Expected " + expectedExitValue + ".");
			}
		}
	}

	/**
	 * Starts a new process and invokes {@link #waitFor(Process, long, TimeUnit)}.
	 * 
	 * @param processBuilder The process builder used to start the new process
	 * @param timeout Maximum time to wait
	 * @param unit Time unit of <code>timeout</code>
	 * @return The finished process.
	 * @throws CommandFailedException If an I/O error occurs when starting the process.
	 * @throws CommandTimeoutException Thrown in case of a timeout
	 */
	public static Process startAndWaitFor(ProcessBuilder processBuilder, long timeout, TimeUnit unit) throws CommandFailedException, CommandTimeoutException {
		try {
			Process proc = processBuilder.start();
			waitFor(proc, timeout, unit);
			return proc;
		} catch (IOException e) {
			throw new CommandFailedException(e);
		}
	}

	/**
	 * Waits for the process to terminate or throws an exception if it fails to do so within the given timeout.
	 * 
	 * @param proc A started process
	 * @param timeout Maximum time to wait
	 * @param unit Time unit of <code>timeout</code>
	 * @throws CommandTimeoutException Thrown in case of a timeout
	 */
	public static void waitFor(Process proc, long timeout, TimeUnit unit) throws CommandTimeoutException {
		try {
			boolean finishedInTime = proc.waitFor(timeout, unit);
			if (!finishedInTime) {
				proc.destroyForcibly();
				throw new CommandTimeoutException();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static String toString(InputStream in, Charset charset) throws IOException {
		return CharStreams.toString(new InputStreamReader(in, charset));
	}

	public static class CommandTimeoutException extends CommandFailedException {

		public CommandTimeoutException() {
			super("Command timed out.");
		}

	}

}

package org.cryptomator.frontend.fuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class OpenFileFactory implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFileFactory.class);

	private final ConcurrentMap<Long, OpenFile> openFiles = new ConcurrentHashMap<>();
	private final AtomicLong fileHandleGen = new AtomicLong(1l);

	/**
	 * @param path path of the file to open
	 * @param options file open options
	 * @return file handle used to identify and close open files.
	 * @throws IOException
	 */
	public long open(Path path, OpenOption... options) throws IOException {
		return open(path, Set.of(options));
	}

	/**
	 *
	 * @param path path of the file to open
	 * @param options file open options
	 * @param attrs file attributes to set when opening
	 * @return file handle used to identify and close open files.
	 * @throws IOException
	 */
	public long open(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		long fileHandle = fileHandleGen.getAndIncrement();
		OpenFile file = OpenFile.create(path, options, attrs);
		openFiles.put(fileHandle, file);
		LOG.trace("Opening {} {}", fileHandle, file);
		return fileHandle;
	}

	public OpenFile get(Long fileHandle) {
		return openFiles.get(fileHandle);
	}

	/**
	 * Closes the channel identified by the given fileHandle
	 * 
	 * @param fileHandle file handle used to identify
	 * @throws ClosedChannelException If no channel for the given fileHandle has been found.
	 * @throws IOException
	 */
	public void close(long fileHandle) throws ClosedChannelException, IOException {
		OpenFile file = openFiles.remove(fileHandle);
		if (file != null) {
			LOG.trace("Releasing {} {}", fileHandle, file);
			file.close();
		} else {
			throw new ClosedChannelException();
		}
	}

	/**
	 * Tests, if any {@link OpenFile} is considered <em>dirty</em>, e.g. might have pending changes.
	 * This method is neither atomic regarding the set of OpenFiles nor regarding each OpenFile individually. Therefore, external synchronization is required.
	 *
	 * @return {@code true} if and only if at least one dirty {@link OpenFile} exists. Otherwise {@code false}.
	 * @see OpenFile#isDirty()
	 */
	boolean hasDirtyFiles() {
		return openFiles.entrySet().stream().anyMatch(entry -> entry.getValue().isDirty());
	}

	/**
	 * Closes all currently open files.
	 * Calling this method will not prevent the factory to open new files, i.e. this method can be called multiple times and is not idempotent.
	 * 
	 * @throws IOException
	 */
	@Override
	public synchronized void close() throws IOException {
		IOException exception = new IOException("At least one open file could not be closed.");
		for (var it = openFiles.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			OpenFile openFile = entry.getValue();
			LOG.warn("Closing unclosed file {}", openFile);
			try {
				openFile.close();
			} catch (IOException e) {
				exception.addSuppressed(e);
			}
			it.remove();
		}
		if (exception.getSuppressed().length > 0) {
			throw exception;
		}
	}

}

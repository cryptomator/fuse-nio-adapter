package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PerAdapter
public class OpenFileFactory implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFileFactory.class);

	private final ConcurrentMap<Long, OpenFile> openFiles = new ConcurrentHashMap<>();
	private final AtomicLong fileHandleGen = new AtomicLong();

	@Inject
	public OpenFileFactory() {
	}

	/**
	 * @param path path of the file to open
	 * @param options file open options
	 * @return file handle used to identify and close open files.
	 * @throws IOException
	 */
	public long open(Path path, OpenOption... options) throws IOException {
		return this.open(path, Sets.newHashSet(options));
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
		openFiles.put(fileHandle, new OpenFile(path, options, attrs));
		return fileHandle;
	}

	public OpenFile get(Long fileHandle) {
		return openFiles.get(fileHandle);
	}

	public void close(long fileHandle) throws IOException {
		OpenFile file = openFiles.remove(fileHandle);
		if (file != null) {
			file.close();
		} else {
			LOG.warn("Attempted to close file that has not been opened or already been closed.");
		}
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
		for (Iterator<Map.Entry<Long, OpenFile>> it = openFiles.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Long, OpenFile> entry = it.next();
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

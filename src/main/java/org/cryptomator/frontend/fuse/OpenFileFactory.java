package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PerAdapter
public class OpenFileFactory implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFileFactory.class);

	private final ConcurrentMap<Path, OpenFile> openFiles = new ConcurrentHashMap<>();

	@Inject
	public OpenFileFactory() {
	}

	public void open(Path path, OpenOption... options) throws IOException {
		try {
			openFiles.computeIfAbsent(path, p -> new OpenFile(path, options));
		} catch (UncheckedIOException e) {
			throw new IOException(e);
		}
	}

	public OpenFile get(Path path) {
		return openFiles.get(path);
	}

	public void close(Path path) throws IOException {
		OpenFile file = openFiles.remove(path);
		file.close();
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
		for (Path p : openFiles.keySet()) {
			try {
				LOG.warn("Closing unclosed file {}", p);
				close(p);
			} catch (IOException e) {
				exception.addSuppressed(e);
			}
		}
		if (exception.getSuppressed().length > 0) {
			throw exception;
		}
	}

}

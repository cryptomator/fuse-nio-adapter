package org.cryptomator.frontend.fuse;

import java.nio.file.FileSystemException;

/**
 * Exception thrown when the requested file is of an unsupported type
 */
public class UnsupportedFileTypeException extends FileSystemException {
	public UnsupportedFileTypeException(String file) {
		super(file);
	}
}

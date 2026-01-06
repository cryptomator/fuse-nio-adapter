package org.cryptomator.frontend.fuse;

import java.nio.file.FileSystemException;

public class UnsupportedFileTypeException extends FileSystemException {
	public UnsupportedFileTypeException(String file) {
		super(file);
	}
}

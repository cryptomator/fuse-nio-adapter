package org.cryptomator.frontend.fuse;

import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

@PerAdapter
public class ReadWriteFileHandler extends ReadOnlyFileHandler implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteFileHandler.class);

	private final OpenFileFactory openFiles;

	@Inject
	public ReadWriteFileHandler(OpenFileFactory openFiles) {
		super(openFiles);
		this.openFiles = openFiles;
	}

}

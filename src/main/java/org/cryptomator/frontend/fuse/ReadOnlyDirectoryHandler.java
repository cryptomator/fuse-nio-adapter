package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Stat;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.stream.Stream;

public class ReadOnlyDirectoryHandler {

	private final FileNameTranscoder fileNameTranscoder;

	public ReadOnlyDirectoryHandler(FileNameTranscoder fileNameTranscoder) {
		this.fileNameTranscoder = fileNameTranscoder;
	}

	public int getattr(Path path, BasicFileAttributes attrs, Stat stat) {
		if (attrs instanceof PosixFileAttributes posixAttrs) {
			stat.setPermissions(posixAttrs.permissions());
		} else {
			stat.setMode(0555);
		}
		FileAttributesUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return 0;
	}

	public int readdir(Path path, DirFiller filler, long offset, FileInfo fi) throws IOException {
		// just fill in names, getattr gets called for each entry anyway
		filler.fill(fileNameTranscoder.nioToFuse("."), stat -> stat.setModeBits(Stat.S_IFDIR));
		filler.fill(fileNameTranscoder.nioToFuse(".."), stat -> stat.setModeBits(Stat.S_IFDIR));
		try (Stream<Path> ds = Files.list(path)) {
			var iter = ds.iterator();
			while (iter.hasNext()) {
				var file = iter.next();
				//introduced due to https://github.com/cryptomator/cryptomator/issues/4319
				//TODO: evaluate after March 2027 if still  needed or fully switch to readdir+
				if( OS.current() == OS.LINUX) {
					filler.fill(fileNameTranscoder.nioToFuse(file.getFileName().toString()), stat -> fillFileType(file, stat));
				} else {
					filler.fill(fileNameTranscoder.nioToFuse(file.getFileName().toString()));
				}
			}
			return 0;
		} catch (DirectoryIteratorException e) {
			throw new IOException(e);
		}
	}

	private void fillFileType(Path file, Stat stat) {
		try {
			var attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			stat.unsetModeBits(Stat.S_IFMT);
			if (attrs.isDirectory()) {
				stat.setModeBits(Stat.S_IFDIR);
			} else if (attrs.isSymbolicLink()) {
				stat.setModeBits(Stat.S_IFLNK);
			} else if (attrs.isRegularFile()) {
				stat.setModeBits(Stat.S_IFREG);
			} 
		} catch (IOException e) {
			
		}
		// leave mode unset -> DT_UNKNOWN, same as before
	}

}

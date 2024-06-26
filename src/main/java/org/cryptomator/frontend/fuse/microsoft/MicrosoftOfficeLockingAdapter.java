package org.cryptomator.frontend.fuse.microsoft;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MicrosoftOfficeLockingAdapter implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(MicrosoftOfficeLockingAdapter.class);

	private final FuseNioAdapter delegate;

	public MicrosoftOfficeLockingAdapter(FuseNioAdapter delegate) {
		this.delegate = delegate;
	}

	public boolean isInUse() {
		return delegate.isInUse();
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}

	public Errno errno() {
		return delegate.errno();
	}

	public Set<Operation> supportedOperations() {
		return delegate.supportedOperations();
	}

	public int getattr(String path, Stat stat, @Nullable FileInfo fi) {
		return delegate.getattr(path, stat, fi);
	}

	public int readlink(String path, ByteBuffer buf, long len) {
		return delegate.readlink(path, buf, len);
	}

	public int mknod(String path, short mode, int rdev) {
		return delegate.mknod(path, mode, rdev);
	}

	public int mkdir(String path, int mode) {
		return delegate.mkdir(path, mode);
	}

	public int unlink(String path) {
		int result = delegate.unlink(path);
		if(ownedLockFiles.containsKey(path) && result == 0) {
			ownedLockFiles.remove(path);
		}
		return result;
	}

	public int rmdir(String path) {
		return delegate.rmdir(path);
	}

	public int symlink(String linkname, String target) {
		return delegate.symlink(linkname, target);
	}

	public int rename(String oldpath, String newpath, int flags) {
		return delegate.rename(oldpath, newpath, flags);
	}

	public int link(String linkname, String target) {
		return delegate.link(linkname, target);
	}

	public int chmod(String path, int mode, @Nullable FileInfo fi) {
		return delegate.chmod(path, mode, fi);
	}

	public int chown(String path, int uid, int gid, @Nullable FileInfo fi) {
		return delegate.chown(path, uid, gid, fi);
	}

	public int truncate(String path, long size, @Nullable FileInfo fi) {
		return delegate.truncate(path, size, fi);
	}

	private static final String LOCK_FILE_PREFIX = "~$";
	private static final Set<StandardOpenOption> CHECKED_OPEN_OPTIONS = Set.of(StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING);
	private final ConcurrentHashMap<String, Boolean> ownedLockFiles = new ConcurrentHashMap<>();

	/*
		What is a lock File?
		1. Its name starts with LOCK_FILE_PREFIX
		2. A sibling file which fileName ends with lockFileName.substring(2) exists
	 */

	public int open(String path, FileInfo fi) {
		if (path.equals("/")) {
			return delegate.open(path, fi);
		} else {
			if (fi.getOpenFlags().stream().anyMatch(CHECKED_OPEN_OPTIONS::contains)) {
				//TODO: should we check if path points to a directory?
				var lockPAndF = constructLockFilePath(path);
				var lockFilePath = lockPAndF.parentPath + "/" + lockPAndF.fileName;
				LOG.info("For path {} lock file name would be {}", path, lockPAndF.fileName);
				if (!ownedLockFiles.containsKey(lockFilePath) && access(lockFilePath, 0) == 0) {
					LOG.info("Lock File for {} found.", path);
					return errno().enolck(); //enolck is converted to STATUS_LOCK_NOT_GRANTED by winfsp
				}
			}
			return delegate.open(path, fi);
		}
	}

	//TODO: test
	private ParentAndFile constructLockFilePath(String path) {
		int j = path.lastIndexOf('/');
		var fileName = path.substring(j + 1);

		var i = fileName.lastIndexOf('.');
		String nameWithoutExtension = i > 0 ? fileName.substring(0, i) : fileName;
		if (nameWithoutExtension.length() < 6) {
			return new ParentAndFile(path.substring(0, j), LOCK_FILE_PREFIX + fileName);
		} else {
			return new ParentAndFile(path.substring(0, j), LOCK_FILE_PREFIX + fileName.substring(1));
		}
	}

	record FileAndLockFile(String parentPath, String fileName, String lockFileName) {

		static FileAndLockFile create(String path) {
			int i = path.lastIndexOf('/');
			var parent = path.substring(0, i);
			var fileName2 = path.substring(i + 1);


			i = fileName2.lastIndexOf('.');
			String lockFileName2 = null;
			if (i > 0) {
				var namePart = fileName2.substring(0, i);
				if (namePart.length() < 6) {
					lockFileName2 = LOCK_FILE_PREFIX + fileName2;
				} else {
					lockFileName2 = LOCK_FILE_PREFIX + fileName2.substring(2);
				}
			}
			return new FileAndLockFile(parent, fileName2, lockFileName2);
		}
	}

	record ParentAndFile(String parentPath, String fileName) {
		static ParentAndFile parse(String path) {
			int j = path.lastIndexOf('/');
			return new ParentAndFile(path.substring(0, j==0? 1:j), path.substring(j + 1));
		}
	}


	public int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
		return delegate.read(path, buf, count, offset, fi);
	}

	public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
		return delegate.write(path, buf, count, offset, fi);
	}

	public int statfs(String path, Statvfs statvfs) {
		return delegate.statfs(path, statvfs);
	}

	public int flush(String path, FileInfo fi) {
		return delegate.flush(path, fi);
	}

	public int release(String path, FileInfo fi) {
		return delegate.release(path, fi);
	}

	public int fsync(String path, int datasync, FileInfo fi) {
		return delegate.fsync(path, datasync, fi);
	}

	public int setxattr(String path, String name, ByteBuffer value, int flags) {
		return delegate.setxattr(path, name, value, flags);
	}

	public int getxattr(String path, String name, ByteBuffer value) {
		return delegate.getxattr(path, name, value);
	}

	public int listxattr(String path, ByteBuffer list) {
		return delegate.listxattr(path, list);
	}

	public int removexattr(String path, String name) {
		return delegate.removexattr(path, name);
	}

	public int opendir(String path, FileInfo fi) {
		return delegate.opendir(path, fi);
	}

	public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
		return delegate.readdir(path, filler, offset, fi, flags);
	}

	public int releasedir(@Nullable String path, FileInfo fi) {
		return delegate.releasedir(path, fi);
	}

	public int fsyncdir(@Nullable String path, int datasync, FileInfo fi) {
		return delegate.fsyncdir(path, datasync, fi);
	}

	public void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
		delegate.init(conn, cfg);
	}

	public void destroy() {
		delegate.destroy();
	}

	public int access(String path, int mask) {
		return delegate.access(path, mask);
	}

	public int create(String path, int mode, FileInfo fi) {
		var pAndF = ParentAndFile.parse(path);
		boolean isLockFile = false;
		if(pAndF.fileName().startsWith("~$")) {
			LOG.info("Path {} is possible lock file. Analyzing...", path);
			var targetFileNameSuffix = pAndF.fileName().substring(2);
			//TODO: very inefficient due to complete dir listing
			// ideas: cache alle paths shortly (e.g. 1 second) and search this cache
			AtomicInteger siblingCounter = new AtomicInteger(0);
			readdir(pAndF.parentPath, ((name, stat, offset, flags) -> {
				if(name.endsWith(targetFileNameSuffix)) {
					siblingCounter.incrementAndGet();
				}
				return 0;
			}) ,0, null, 0); //TODO: fileinfo can be null, because ReadOnlyDirHandler does not use it
			isLockFile = siblingCounter.get() > 0;
			if( isLockFile ) {
				LOG.info("Path {} is a lock file.", path);
			} else {
				LOG.info("Path {} is regular file.", path);
			}
		}

		int result = delegate.create(path, mode, fi);
		if(isLockFile && result == 0) {
			ownedLockFiles.put(path, true);
		}
		return result;
	}

	public int utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) {
		return delegate.utimens(path, atime, mtime, fi);
	}

	public int ioctl(String path, int cmd, ByteBuffer arg, FileInfo fi, int flags, @Nullable ByteBuffer data) {
		return delegate.ioctl(path, cmd, arg, fi, flags, data);
	}

	public int flock(String path, FileInfo fi, int op) {
		return delegate.flock(path, fi, op);
	}

	public int fallocate(String path, int mode, long offset, long length, FileInfo fi) {
		return delegate.fallocate(path, mode, offset, length, fi);
	}

}

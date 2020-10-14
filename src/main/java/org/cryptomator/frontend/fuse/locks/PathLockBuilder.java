package org.cryptomator.frontend.fuse.locks;

public interface PathLockBuilder {

	PathLock forReading();

	PathLock tryForReading() throws AlreadyLockedException;

	PathLock forWriting();

	PathLock tryForWriting() throws AlreadyLockedException;

}

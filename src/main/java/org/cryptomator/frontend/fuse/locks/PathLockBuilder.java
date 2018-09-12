package org.cryptomator.frontend.fuse.locks;

public interface PathLockBuilder {

	PathLock forReading();

	PathLock forWriting();

}

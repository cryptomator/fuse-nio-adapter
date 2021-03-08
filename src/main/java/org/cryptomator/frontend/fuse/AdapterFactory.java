package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class AdapterFactory {

	private static final int DEFAULT_NAME_MAX = 254; // 255 is preferred, but nautilus checks for this value + 1

	private AdapterFactory() {
	}

	/**
	 * Creates a read-only fuse-nio filesystem with a maximum file name length of {@value DEFAULT_NAME_MAX} and an assumed filename encoding of UTF-8 NFC for FUSE and the NIO filesystem.
	 * @param root the root path of the NIO filesystem.
	 * @return an adapter mapping FUSE callbacks to the nio interface
	 * @see ReadOnlyAdapter
	 * @see FileNameTranscoder
	 */
	public static FuseNioAdapter createReadOnlyAdapter(Path root) {
		return createReadOnlyAdapter(root, DEFAULT_NAME_MAX, FileNameTranscoder.transcoder() );
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder()
				.root(root)
				.maxFileNameLength(maxFileNameLength)
				.fileNameTranscoder(fileNameTranscoder)
				.build();
		return comp.readOnlyAdapter();
	}

	/**
	 * Creates a fuse-nio-filesystem with a maximum file name length of {@value DEFAULT_NAME_MAX} and an assumed filename encoding of UTF-8 NFC for FUSE and the NIO filesystem.
	 * @param root the root path of the NIO filesystem.
	 * @return an adapter mapping FUSE callbacks to the nio interface
	 * @see ReadWriteAdapter
	 * @see FileNameTranscoder
	 */
	public static FuseNioAdapter createReadWriteAdapter(Path root) {
		return createReadWriteAdapter(root, DEFAULT_NAME_MAX);
	}

	/**
	 * Creates a fuse-nio-filesystem with an assumed filename encoding of UTF-8 NFC for FUSE and the NIO filesystem.
	 * @param root the root path of the NIO filesystem.
	 * @return an adapter mapping FUSE callbacks to the nio interface
	 * @see ReadWriteAdapter
	 * @see FileNameTranscoder
	 */
	public static FuseNioAdapter createReadWriteAdapter(Path root, int maxFileNameLength) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).maxFileNameLength(maxFileNameLength).fileNameTranscoder(FileNameTranscoder.transcoder()).build();
		return comp.readWriteAdapter();
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).maxFileNameLength(maxFileNameLength).fileNameTranscoder(fileNameTranscoder).build();
		return comp.readWriteAdapter();
	}
}

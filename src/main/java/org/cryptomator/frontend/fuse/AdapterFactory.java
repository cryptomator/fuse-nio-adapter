package org.cryptomator.frontend.fuse;

import org.cryptomator.frontend.fuse.encoding.BufferEncoder;
import org.cryptomator.frontend.fuse.encoding.DefaultEncoder;

import java.nio.file.Path;

public class AdapterFactory {

	private static final int DEFAULT_NAME_MAX = 254; // 255 is preferred, but nautilus checks for this value + 1

	private AdapterFactory() {
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root) {
		return createReadOnlyAdapter(root, DEFAULT_NAME_MAX, new DefaultEncoder());
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root, int maxFileNameLength, BufferEncoder toFuseEncoder ) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder()
				.root(root)
				.maxFileNameLength(maxFileNameLength)
				.toFuseEncoder(toFuseEncoder)
				.build();
		return comp.readOnlyAdapter();
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root) {
		return createReadWriteAdapter(root, DEFAULT_NAME_MAX);
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root, int maxFileNameLength) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).maxFileNameLength(maxFileNameLength).toFuseEncoder(new DefaultEncoder()).build();
		return comp.readWriteAdapter();
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root, int maxFileNameLength, BufferEncoder toFuseEncoder) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).maxFileNameLength(maxFileNameLength).toFuseEncoder(toFuseEncoder).build();
		return comp.readWriteAdapter();
	}
}

package org.cryptomator.frontend.fuse.mount;

import jnr.ffi.Pointer;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import ru.serce.jnrfuse.FuseFS;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

class MountSuccessFuseDecorator implements FuseFS {

    private final FuseNioAdapter delegate;
    private final CountDownLatch mountSuccessSignal;

    MountSuccessFuseDecorator(FuseNioAdapter delegate, CountDownLatch mountSuccessSignal) {
        this.delegate = delegate;
        this.mountSuccessSignal = mountSuccessSignal;
    }

    @Override
    public Pointer init(Pointer conn) {
        mountSuccessSignal.countDown();
        return delegate.init(conn);
    }


    // delegates

    @Override
    public int getattr(String path, FileStat stat) {
        return delegate.getattr(path, stat);
    }

    @Override
    public int readlink(String path, Pointer buf, long size) {
        return delegate.readlink(path, buf, size);
    }

    @Override
    public int mknod(String path, long mode, long rdev) {
        return delegate.mknod(path, mode, rdev);
    }

    @Override
    public int mkdir(String path, long mode) {
        return delegate.mkdir(path, mode);
    }

    @Override
    public int unlink(String path) {
        return delegate.unlink(path);
    }

    @Override
    public int rmdir(String path) {
        return delegate.rmdir(path);
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        return delegate.symlink(oldpath, newpath);
    }

    @Override
    public int rename(String oldpath, String newpath) {
        return delegate.rename(oldpath, newpath);
    }

    @Override
    public int link(String oldpath, String newpath) {
        return delegate.link(oldpath, newpath);
    }

    @Override
    public int chmod(String path, long mode) {
        return delegate.chmod(path, mode);
    }

    @Override
    public int chown(String path, long uid, long gid) {
        return delegate.chown(path, uid, gid);
    }

    @Override
    public int truncate(String path, long size) {
        return delegate.truncate(path, size);
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return delegate.open(path, fi);
    }

    @Override
    public int read(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
        return delegate.read(path, buf, size, offset, fi);
    }

    @Override
    public int write(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
        return delegate.write(path, buf, size, offset, fi);
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return delegate.statfs(path, stbuf);
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return delegate.flush(path, fi);
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        return delegate.release(path, fi);
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        return delegate.fsync(path, isdatasync, fi);
    }

    @Override
    public int setxattr(String path, String name, Pointer value, long size, int flags) {
        return delegate.setxattr(path, name, value, size, flags);
    }

    @Override
    public int getxattr(String path, String name, Pointer value, long size) {
        return delegate.getxattr(path, name, value, size);
    }

    @Override
    public int listxattr(String path, Pointer list, long size) {
        return delegate.listxattr(path,list, size);
    }

    @Override
    public int removexattr(String path, String name) {
        return delegate.removexattr(path, name);
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        return delegate.opendir(path, fi);
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
        return delegate.readdir(path, buf, filter, offset, fi);
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        return delegate.releasedir(path, fi);
    }

    @Override
    public int fsyncdir(String path, FuseFileInfo fi) {
        return delegate.fsyncdir(path, fi);
    }

    @Override
    public void destroy(Pointer initResult) {
        delegate.destroy(initResult);
    }

    @Override
    public int access(String path, int mask) {
        return delegate.access(path, mask);
    }

    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        return delegate.create(path, mode, fi);
    }

    @Override
    public int ftruncate(String path, long size, FuseFileInfo fi) {
        return delegate.ftruncate(path, size, fi);
    }

    @Override
    public int fgetattr(String path, FileStat stbuf, FuseFileInfo fi) {
        return delegate.fgetattr(path, stbuf, fi);
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        return delegate.lock(path, fi, cmd, flock);
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        return delegate.utimens(path, timespec);
    }

    @Override
    public int bmap(String path, long blocksize, long idx) {
        return delegate.bmap(path, blocksize, idx);
    }

    @Override
    public int ioctl(String path, int cmd, Pointer arg, FuseFileInfo fi, long flags, Pointer data) {
        return delegate.ioctl(path, cmd, arg, fi, flags, data);
    }

    @Override
    public int poll(String path, FuseFileInfo fi, FusePollhandle ph, Pointer reventsp) {
        return delegate.poll(path, fi, ph, reventsp);
    }

    @Override
    public int write_buf(String path, FuseBufvec buf, long off, FuseFileInfo fi) {
        return delegate.write_buf(path, buf, off, fi);
    }

    @Override
    public int read_buf(String path, Pointer bufp, long size, long off, FuseFileInfo fi) {
        return delegate.read_buf(path, bufp, size, off, fi);
    }

    @Override
    public int flock(String path, FuseFileInfo fi, int op) {
        return delegate.flock(path, fi, op);
    }

    @Override
    public int fallocate(String path, int mode, long off, long length, FuseFileInfo fi) {
        return delegate.fallocate(path, mode, off, length, fi);
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
        delegate.mount(mountPoint, blocking, debug, fuseOpts);
    }

    @Override
    public void umount() {
        delegate.umount();
    }
}

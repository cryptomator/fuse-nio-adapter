package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import jnr.ffi.Pointer;
import jnr.ffi.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Instant;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 * TODO: get the current user and save it as the file owner!
 */
@PerAdapter
public class ReadWriteAdapter extends ReadOnlyAdapter implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyDirectoryHandler.class);
	private final Path root;
	private final ReadWriteDirectoryHandler dirHandler;
	private final ReadWriteFileHandler fileHandler;

	@Inject
	public ReadWriteAdapter(@Named("root") Path root, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler) {
		super(root, dirHandler, fileHandler);
		this.root = root;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
	}

	@Override
	public int mknod(String var1, @mode_t long var2, @dev_t long var4) {
		Path absPath = resolvePath(var1);
		if(Files.isDirectory(absPath)){
			return -ErrorCodes.EISDIR();
		}
		else if(Files.exists(absPath)){
			return -ErrorCodes.EEXIST();
		}
		else {
			try{
				//TODO: take POSIX permisiions in var2 into FileAttributes!
				Files.createFile(absPath);
				return 0;
			}
			catch (IOException e) {
				return -ErrorCodes.EIO();
			}
		}
	}

	@Override
	public int mkdir(String path, @mode_t long mode){
	    Path node = resolvePath(path);
	    if(Files.exists(node)){
	    	return  -ErrorCodes.EEXIST();
		}
		else{
			try {
				Files.createDirectory(node);
				Files.setLastModifiedTime(node, FileTime.from(Instant.now()));
				//TODO: maybe create an instance of the lookup service as a class variable
				UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
				LOG.info("XXX Current owner is ");
				Files.setOwner(node, lookupService.lookupPrincipalByName(System.getProperty("user.name")));
				//TODO: set cTime
				Files.setAttribute(node, "lastAccessTime", FileTime.from(Instant.now()));
				Files.setAttribute(node, "creationTime", FileTime.from(Instant.now()));
				Files.setAttribute(node, "isDirectory", true);
				//TODO: check if this is the right permission!
				Files.setPosixFilePermissions(node, PosixFilePermissions.fromString("rwxr-xr-x"));
				return 0;
			} catch (IOException e) {
				LOG.error("Exception occured",e);
				return -ErrorCodes.EIO();
			}
		}
	}
	
		@Override
	public int create(String path, @mode_t long mode, FuseFileInfo fi){
		Path node = resolvePath(path);
		if(Files.exists(node)){
			return -ErrorCodes.EEXIST();
		}
		else{
			//TODO: create PosixFileAttribute
			try {
				Files.createFile(node,null);
				return 0;
			} catch (IOException e) {
				LOG.error("Exception occured",e);
			    return -ErrorCodes.EIO();
			}
		}

	}

	@Override
	public int chown(String path, @uid_t long uid, @gid_t long gid){
		Path node =resolvePath(path);
		if(!Files.exists(node)){
			return -ErrorCodes.ENOENT();
		}
		else{
			try {
				Files.setAttribute(node,"unix:uid", uid);
				Files.setAttribute(node, "unix:gid", gid);
				return 0;
			} catch (IOException e) {
				e.printStackTrace();
				return -ErrorCodes.EIO();
			}
		}
	}

	@Override
	public int chmod(String path, @mode_t long mode){
		Path node =resolvePath(path);
		if(!Files.exists(node)){
			return -ErrorCodes.ENOENT();
		}
		else{
			try {
				String perm = octalPermToLetters(mode);
				Files.setPosixFilePermissions(node, PosixFilePermissions.fromString(perm));
				return 0;
			} catch (IOException e) {
				e.printStackTrace();
				return -ErrorCodes.EIO();
			}
		}
	}

	//TODO: check if this function does the right thing
	private String octalPermToLetters(@mode_t long mode){
		String perm ="";
		for(int i=2; i>=0; i++){
			int role = (int) (mode/((int) (Math.pow(10,i))))%10;
			String rolePerm ="";
			if(role == 4){
				rolePerm.concat("rwx");
			}else if(role == 2){
				rolePerm.concat("rw-");
			}else if(role == 1){
				rolePerm.concat("r--");
			}
			else{
				rolePerm.concat("---");
			}
			perm = perm.concat(rolePerm);
		}
		return perm;
	}
	

	@Override
	public int unlink(String var1){
		Path absPath = resolvePath(var1);
		if(!Files.exists(absPath)){
			return -ErrorCodes.ENOENT();
		}
		else if(Files.isDirectory(absPath)){
			return -ErrorCodes.EISDIR();
		}
		else{
			try{
				Files.delete(absPath);
				return 0;
			} catch (IOException e) {
				LOG.info("Error:",e);
				return -ErrorCodes.EIO();
			}
		}

	}

	@Override
	public int rmdir(String path){
		Path absPath = resolvePath(path);
		if(!Files.exists(absPath)){
			return -ErrorCodes.ENOENT();
		}
		else if(Files.isDirectory(absPath)){
			try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(absPath)) {
				if(dirStream.iterator().hasNext()){
					return -ErrorCodes.ENOTEMPTY();
				}
				else{
					Files.delete(absPath);
					return 0;
				}
			} catch (IOException e) {
				return -ErrorCodes.EIO();
			}
		}
		else{
			return -ErrorCodes.ENOTDIR();
		}
	}
}


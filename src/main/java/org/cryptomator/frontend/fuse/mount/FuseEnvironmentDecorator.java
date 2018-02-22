package org.cryptomator.frontend.fuse.mount;

public abstract class FuseEnvironmentDecorator implements FuseEnvironment{

	public FuseEnvironment parent;

	public FuseEnvironment setParent(FuseEnvironment fe){
		parent = fe;
		return this;
	}
}

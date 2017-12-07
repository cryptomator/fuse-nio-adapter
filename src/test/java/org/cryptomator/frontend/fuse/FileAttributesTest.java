package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class FileAttributesTest {

	public static void main(String args[]) {
		Path file = Paths.get("/home/alf/Arbeit/Skymatic/test-env/test2");
		try {
			Map<String, Object> attrs = Files.readAttributes(file, "*");
			Set<String> props = attrs.keySet();
			for (String prop : props) {
				System.out.println(prop.toString() + " : " + attrs.get(prop));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

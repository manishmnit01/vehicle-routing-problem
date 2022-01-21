package com.tg;

import com.tg.routing.NativeUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MainApplication {

	private static String OS = System.getProperty("os.name").toLowerCase();

	private static boolean IS_WINDOWS = OS.contains("win");

	private static boolean IS_UNIX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

	public static void main(String args[]) {
		SpringApplication.run(MainApplication.class, args);

		// Load native library for google or tools
		try {
			if(IS_WINDOWS) {
				//NativeLoader.loadLibrary("jniortools");
				NativeUtils.loadLibraryFromJar("/natives/windows/jniortools.dll");
			} else if (IS_UNIX) {
				//System.load("/home/ubuntu/vehicle-routing-service/libjniortools.so");
				NativeUtils.loadLibraryFromJar("/natives/linux/libortools.so");
				NativeUtils.loadLibraryFromJar("/natives/linux/libjniortools.so");
			} else {
				throw new RuntimeException("Could not determine operating system.");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
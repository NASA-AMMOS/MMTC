package edu.jhuapl.sd.sig.mmtc.util;

import java.io.IOException;
import java.lang.management.ManagementFactory;

public class Environment {
	public static String getEnvironmentVariable(String name) {
		return System.getenv(name);
	}

	public static boolean isPidRunning(String pid) {
		try {
			Process process = Runtime.getRuntime().exec(
					new String[]{"/usr/bin/kill", "-0", pid} // kill -0 returns exit code 0 if process exists, doesn't send any signals
			);
			return process.waitFor() == 0;
		} catch (IOException | InterruptedException e) {
			return true;
		}
	}

	public static String getMyPid() {
		return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	}
}

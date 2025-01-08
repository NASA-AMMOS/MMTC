package edu.jhuapl.sd.sig.mmtc.util;

public class Environment {
	public static String getEnvironmentVariable(String name) {
		return System.getenv(name);
	}
}

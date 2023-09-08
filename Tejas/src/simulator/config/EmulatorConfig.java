package config;

public class EmulatorConfig {
	
	public static CommunicationType communicationType;
	public static EmulatorType emulatorType;

	public static int maxThreadsForTraceCollection = 1024;
	public static boolean storeExecutionTraceInAFile;
	public static String basenameForTraceFiles;
	
	public static String PinTool = null;
	public static String PinInstrumentor = null;
	public static String QemuTool = null;
	public static String ShmLibDirectory;
	public static String KillEmulatorScript;

	public static long IP_Offset = 0; // 0 for statically compiled execs, 0x5555 5555 4000 for non statically compiled 
	public static long first_IP = -1;
}

package main;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;




import config.EmulatorConfig;
import config.EmulatorType;
import config.SimulationConfig;
import config.SystemConfig;

public class Emulator {
	
	private Process emulatorProcess;
	private String OS;	
	private boolean isStreamGobblerNeeded = true;
	
	TejasStreamGobbler s1;
	TejasStreamGobbler s2;
	
	public Emulator(String pinTool, String pinInstrumentor, 
			String executableArguments, int pid) 
	{
		//try{
			//recordBinaryOffset(executableArguments);
		//} catch(Exception e){
		//	System.out.println("Tejas could not find the offset for the instrumented binary. Here's hoping it is statically compiled!!");
		//}
		OS = System.getProperty("os.name").toLowerCase();
		System.out.println("subset sim size = "  + 
				SimulationConfig.subsetSimSize + "\t" + 
				SimulationConfig.subsetSimulation);
		
		System.out.println("marker functions = "  + SimulationConfig.markerFunctionsSimulation 
				+ "\t start marker = " + SimulationConfig.startMarker
				+ "\t end marker = " + SimulationConfig.endMarker);

		// Creating command for PIN tool.
		StringBuilder pin = null;
		
		if(new File(pinTool + "/pin.sh").exists())
		{
			pin = new StringBuilder(pinTool + "/pin.sh");
		}
		else
		{
			pin = new StringBuilder(pinTool + "/pin");
		}
		StringBuilder cmd;
		if(OS.indexOf("win") >= 0)	{
			 cmd = new StringBuilder(pin +  //" -injection child "+
					" -t " + pinInstrumentor +
					" -maxNumActiveThreads " + (SystemConfig.maxNumJavaThreads*SystemConfig.numEmuThreadsPerJavaThread) +
					" -map " + SimulationConfig.MapEmuCores +
					" -numIgn " + SimulationConfig.NumInsToIgnore +
					" -numSim " + SimulationConfig.subsetSimSize +
					" -id " + pid + 
					" -traceMethod " + EmulatorConfig.communicationType.toString());
		}
		else{
			 cmd = new StringBuilder(pin + " -injection child "+
					" -t " + pinInstrumentor +
					" -maxNumActiveThreads " + (SystemConfig.maxNumJavaThreads*SystemConfig.numEmuThreadsPerJavaThread) +
					" -map " + SimulationConfig.MapEmuCores +
					" -numIgn " + SimulationConfig.NumInsToIgnore +
					" -numSim " + SimulationConfig.subsetSimSize +
					" -id " + pid + 
					" -traceMethod " + EmulatorConfig.communicationType.toString());
		}		
		if(SimulationConfig.pinpointsSimulation == true)
		{
			cmd.append(" -pinpointsFile " + SimulationConfig.pinpointsFile);
		}
		if(SimulationConfig.startMarker != "")
		{
			cmd.append(" -startMarker " + SimulationConfig.startMarker);
		}
		if(SimulationConfig.endMarker != "")
		{
			cmd.append(" -endMarker " + SimulationConfig.endMarker);
		}
		
		cmd.append(" -- " + executableArguments);
		System.out.println("command is : " + cmd.toString());
		
		startEmulator(cmd.toString());
	}
	
	//This is not being used since file mode  with pin has been deprecated @TODO remove this
	public Emulator(String pinTool, String pinInstrumentor, 
			String executableArguments, String basenameForTraceFile) 
	{
	
		// This constructor is used for trace collection inside a file
		OS = System.getProperty("os.name").toLowerCase();	
		System.out.println("subset sim size = "  + 
				SimulationConfig.subsetSimSize + "\t" + 
				SimulationConfig.subsetSimulation);
		
		System.out.println("marker functions = "  + SimulationConfig.markerFunctionsSimulation 
				+ "\t start marker = " + SimulationConfig.startMarker
				+ "\t end marker = " + SimulationConfig.endMarker);

		// Creating command for PIN tool.
		StringBuilder pin = null;
		
		if(new File(pinTool + "/pin.sh").exists())
		{
			pin = new StringBuilder(pinTool + "/pin.sh");
		}
		else
		{
			pin = new StringBuilder(pinTool + "/pin");
		}

		StringBuilder cmd = new StringBuilder(pin +//  " -injection child "+
				" -t " + pinInstrumentor +
				" -maxNumActiveThreads  " + EmulatorConfig.maxThreadsForTraceCollection +
				" -map " + SimulationConfig.MapEmuCores +
				" -numIgn " + SimulationConfig.NumInsToIgnore +
				" -numSim " + SimulationConfig.subsetSimSize +
				" -traceMethod file -traceFileName " + basenameForTraceFile);
		
		if(SimulationConfig.pinpointsSimulation == true)
		{
			misc.Error.showErrorAndExit("Cannot create a trace file, and a pinpoints file at the same time !!");
		}
		if(SimulationConfig.startMarker != "")
		{
			cmd.append(" -startMarker " + SimulationConfig.startMarker);
		}
		if(SimulationConfig.endMarker != "")
		{
			cmd.append(" -endMarker " + SimulationConfig.endMarker);
		}
		
		cmd.append(" -- " + executableArguments);
		System.out.println("command is : " + cmd.toString());
		
		startEmulator(cmd.toString());
	}

	private void recordBinaryOffset(String exec) throws Exception{

		//if linux kernel >=4.4 
		ProcessBuilder pb = new ProcessBuilder("uname","-r");
		Process unameProcess = pb.start();
		BufferedReader reader = 
                new BufferedReader(new InputStreamReader(unameProcess.getInputStream()));
		StringBuilder builder = new StringBuilder();
		String line = null;
		while ( (line = reader.readLine()) != null) {
			builder.append(line);
			
		}
		String result = builder.toString();
		String[] tokens = result.split("\\.");
		int majorversion = Integer.parseInt(tokens[0]);
		int minorversion = Integer.parseInt(tokens[1]);
		if(majorversion<4 || (majorversion==4 && minorversion<4)){
			//we will not need this offsetting
			//do not continue
			System.out.println("Old linux kernel requires no offsetting in bin");
			return;
		}

		//binary is the first string!!
		String[] bintokens = exec.split(" ");
		String bin = "";
		for(int it =0;it<bintokens.length;it++){
			if(bintokens[it].isEmpty()) continue;

			bin = bintokens[it];
			break;
		}

		//use the binary to figure out wether bin is dynamically compiled or not
		ProcessBuilder pb2 = new ProcessBuilder("ldd",bin);
		Process lddProcess = pb2.start();
		BufferedReader reader2 = 
               new BufferedReader(new InputStreamReader(lddProcess.getInputStream()));
		StringBuilder builder2 = new StringBuilder();
		String line2 = null;
		while ( (line2 = reader2.readLine()) != null) {
			builder2.append(line2);
			//builder2.append(System.getProperty("line.separator"));
		}
		String  result2 = builder2.toString();
		
		//Result2  mights be blank if static exec found
		//"not a dynamic executable" is printed on stderr on some machines
		if(result2.isEmpty() || result2.contains("not a dynamic executable")){
			//binary is statically compiled
			System.out.println("Static binary. No offset needed");
			EmulatorConfig.IP_Offset = 0;
		} else {		
			EmulatorConfig.IP_Offset = 93824992231424L; //0x5555 5555 4000
			System.out.println("Dynamic exec found.. Offsetting by "+ EmulatorConfig.IP_Offset);
		}

	}
	
	public Emulator(String qemuTool)
	{
		OS = System.getProperty("os.name").toLowerCase();
		startEmulator(qemuTool);
	}

	private void printProcMap(long pinpid) throws Exception{
		//command to get the child pid
		//String cmd = "pgrep -P "+ pinpid;
		{
		//if linux kernel >=4.4
                ProcessBuilder pb = new ProcessBuilder("uname","-r");
                Process unameProcess = pb.start();
                BufferedReader reader =
                new BufferedReader(new InputStreamReader(unameProcess.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ( (line = reader.readLine()) != null) {
                        builder.append(line);

                }
                String result = builder.toString();
                String[] tokens = result.split("\\.");
                int majorversion = Integer.parseInt(tokens[0]);
                int minorversion = Integer.parseInt(tokens[1]);
                if(majorversion<4 || (majorversion==4 && minorversion<4)){
                        //we will not need this offsetting
                        //do not continue
                        System.out.println("Old linux kernel requires no offsetting in bin");
                        return;
                }
		}
		ProcessBuilder pb = new ProcessBuilder("pgrep","-P",pinpid+"");
		Process getchildpidProcess = pb.start();
		getchildpidProcess.waitFor();
		BufferedReader reader = 
                new BufferedReader(new InputStreamReader(getchildpidProcess.getInputStream()));
		StringBuilder builder = new StringBuilder();
		String line = null;
		int lines =0;
		while ( (line = reader.readLine()) != null) {
			builder.append(line);
			lines ++;
			//builder.append(System.getProperty("line.separator"));
		}
		if(lines !=1) {
			misc.Error.showErrorAndExit("Multiple children of pin process!!! Could not get instrumented process's PID");
		}
		String result = builder.toString();

		long childpid = Long.parseLong(result);
		System.out.println("Pid of the instrumented process is "+childpid);

		//now we know the child process, we can get the proc map for it

		
		pb = new ProcessBuilder("cat","/proc/"+childpid+"/maps");
		Process getProcMapProcess = pb.start();
		
		reader = new BufferedReader(new InputStreamReader(getProcMapProcess.getInputStream()));
		//builder = new StringBuilder();
		line = null;

		while ( (line = reader.readLine()) != null) {
			System.out.println(line);
			//builder.append(line);
			String[] tokens =line.split(" ");
			//the first token with the benchmark path is the answer to our questions
			//if(tokens[1].equals("r-xp")) {
				if(tokens[tokens.length-1].equals(Main.benchmarkpath))  {
					String startAddress = tokens[0].split("-")[0];
					EmulatorConfig.IP_Offset = Long.parseLong(startAddress,16) ;//- EmulatorConfig.first_IP;
					System.out.println("Found start of address in procmaps "+EmulatorConfig.IP_Offset);

					break;
					//System.out.println(line);
				}
			//}
			//	builder.append(System.getProperty("line.separator"));
		}
		//String procmaps = builder.toString();
		//System.out.println(procmaps);

	}


	public static synchronized long getPidOfPinProcess() {
		long pinpid = -1;
		

		try
		{
			String[] cmd = {
				"/bin/sh",
				"-c",
				"ps -aux | grep \""+ Main.benchmarkpath+"\" | grep -v \"grep\" | grep \"pin\" | awk '{print $2}'"
				};
            Process pidProcess = Runtime.getRuntime().exec(cmd);
            BufferedReader reader =
            new BufferedReader(new InputStreamReader(pidProcess.getInputStream()));
            String line = null;
            while ( (line = reader.readLine()) != null) {
					pinpid = Long.parseLong(line);
            }
		} catch(Exception e) {

		}
		return pinpid;
	}

	// Start the PIN process. Parse the cmd accordingly
	private void startEmulator(String cmd) {
		emulatorCommand = cmd;
		Runtime rt = Runtime.getRuntime();
		try {
			emulatorProcess = rt.exec(cmd);
			//@HACK
			//We need the PID of the child of the pintool
			//Pin wont launch until it does initialization things
			//so to offset that, we just delay for 1 second
			//A better approach would be to add a JNI function that signals a semaphore
			//and we sleep on that semaphore 
			Thread.sleep(1000);
			long pinpid = getPidOfPinProcess();
			printProcMap(pinpid);
			if(isStreamGobblerNeeded==true) {
				s1 = new TejasStreamGobbler ("stdin", emulatorProcess.getInputStream ());
				s2 = new TejasStreamGobbler ("stderr", emulatorProcess.getErrorStream ());
				
				s1.start ();
				s2.start ();
			}
		} catch (Exception e) {
			e.printStackTrace();
			misc.Error.showErrorAndExit("Error in starting the emulator.\n" +
					"Emulator Command : " + cmd);
		}
	}
	
	// Should wait for PIN too before calling the finish function to deallocate stuff related to
	// the corresponding mechanism
	public void waitForEmulator() {
		try {
			emulatorProcess.waitFor();
			if(isStreamGobblerNeeded==true) {
				s1.terminate();
				s2.terminate();
			}
		} catch (Exception e) { }
	}
	
	public void forceKill() {
		s1.terminate();
		s2.terminate();
		emulatorProcess.destroy();
		
		Main.ipcBase.finish();
		
		if(EmulatorConfig.emulatorType==EmulatorType.pin) {
			//System.err.println(errorMessage);
			Process process;
			if(OS.indexOf("win") >= 0)	{
		
				String cmd = "cmd.exe /c " +
		        EmulatorConfig.KillEmulatorScript+" "+String.valueOf(Main.pid);
				try 
				{
					process = Runtime.getRuntime().exec(cmd);
					TejasStreamGobbler s1 = new TejasStreamGobbler ("stdin", process.getInputStream ());
					TejasStreamGobbler s2 = new TejasStreamGobbler ("stderr", process.getErrorStream ());
					s1.start ();
					s2.start ();
					System.out.println("killing emulator process");
					process.waitFor();
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
	
			}
			else{
				  String cmd[] = {"/bin/bash",
				  EmulatorConfig.KillEmulatorScript,
				  String.valueOf(Main.pid)
			 	  };
    			  try 
				  {
						process = Runtime.getRuntime().exec(cmd);
						TejasStreamGobbler s1 = new TejasStreamGobbler ("stdin", process.getInputStream ());
						TejasStreamGobbler s2 = new TejasStreamGobbler ("stderr", process.getErrorStream ());
						s1.start ();
						s2.start ();
						System.out.println("killing emulator process");
						process.waitFor();
				  } 
				  catch (Exception e) 
				  {
						e.printStackTrace();
				  }
			}
		


		}
	}
	
	private static String emulatorCommand = null;

	public static String getEmulatorCommand() {
		return emulatorCommand;
	}
	
	
}

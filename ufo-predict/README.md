# UFO-Predict
Decode, process UFO events and construct constraints to detect UaF


1. Uncompress traces of a process (if the trace is compressed)
java -jar UFOUnzip.jar  $input_dir $output_dir

example:

java -jar UFOUnzip.jar ./ufo_traces_12345  unzipped12345

if there is IO exception, the trace file for one thread is broken, but the traces for the rest of the thread are still useful.

2. config trace analyzer.

	there are two ways to config:
		1. use command line, see "UFOMain -help"
		2. use file "config.properties", "solver_time", "trace_dir", "solver_mem", "window_size" can be configured here. The config.properties will override command line arguments.

3. run trace analyzer, main class file: UFOMain.java

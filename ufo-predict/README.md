# UFO-Predict
Decode, process UFO events and construct constraints to detect UaF

### Configuration

There are two ways to config:

1. use command line, see `UFOMain -help`
		
2. use file `config.properties`, `solver_time`, `trace_dir`, `solver_mem`, `window_size` can be configured. The `config.properties` will override command line arguments.

### Run 

Run the main class file: ```UFOMain.java```

### Optional

If the trace is compressed, you can uncompress the trace using `UFOUnzip.jar` 
```
java -jar UFOUnzip.jar  $input_dir $output_dir
```
Example:
```
java -jar UFOUnzip.jar ./ufo_traces_12345  unzipped12345
```
If there is IO exception, the trace file for one thread is broken, but the traces for the rest of the thread are still useful.


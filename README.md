# UFO

An automated tool to predictively detect use-after-free (UAF) vulnerabilities in multithreaded programs.

Please cite our ICSE'18 paper ["UFO: Predictive Concurrency Use-After-Free Detection"](https://parasol.tamu.edu/people/jeff/academic/ufo.pdf) if you used our resource.

***\*\*\*NEW***
If you want to build LLVM 7.0.0+UFO and Chromium in one fell swoop, use the build script `fullbuild.sh`. The best way to run this script is `./fullbuild.sh`. Note that halfway through you will need to enter your password to download additional binaries (This has only been tested on Ubuntu 16.04.6, but should work for other Linux distros).


## Quick Start

If you just want to try out UFO and do not want to clutter your local host, you can easily build and run LLVM+UFO in a Docker container.

First, install Docker. See https://docs.docker.com/install/.

Pull the Docker image using `docker pull 4ndychin/llvm-ufo` *(Note: the image is about 5 GB).*

Then, you can run a shell containing the build using `docker run -it 4ndychin/llvm-ufo`.

Now you can skip to the UFO Usage Section *(Note: the environment has limited text editing capabilities. Familiarize yourself with the basics of using `vim` if you have not done so).*

## UFO setup

#### Get LLVM 6.0.0 source code

http://releases.llvm.org/ (download `llvm`, `clang`, and `clang-tools-extra`, or use the following scripts)

```
export BRANCH=release_60
git clone http://llvm.org/git/llvm.git -b $BRANCH
git clone http://llvm.org/git/clang.git llvm/tools/clang -b $BRANCH
git clone http://llvm.org/git/clang-tools-extra.git llvm/tools/clang/tools/extra -b $BRANCH
```

replace the folder `$your_llvm_dir$/projects/compiler-rt/lib/tsan` with the `tsan` folder you found in this project

#### Build project

Build step is the same as building LLVM, see also http://clang.llvm.org/get_started.html

Example:
First, create a build dir outside of llvm source dir (in-tree build not supported)
```
cd $outside your_llvm_dir$
mkdir build
cd build/
```

For a faster build, you can exclude LLVM test and examples, e.g.,
```
cmake -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release -DLLVM_BUILD_TESTS=OFF -DLLVM_INCLUDE_TESTS=OFF -DLLVM_BUILD_EXAMPLES=OFF -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_ENABLE_ASSERTIONS=OFF $your_llvm_dir$
make -j10
```

#### Install z3 

Follow https://github.com/Z3Prover/z3

add z3 to your PATH

## UFO Usage

### 1. Instrument code

You can instrument C/C++ source or LLVM bitcode, the way to use UFO is similar to TSAN.
Example:

build & instrument code:

```../$your_llvm_build$/bin/clang -fsanitize=thread -g -o0 -Wall test.cc -o test```

### 2. UFO tracing

Next you can load and execute the code to get traces.

There are several environment variables you can set to config the tracing:

- **UFO_ON** (Boolean): set to __1__ to enable UFO tracing, or __0__ to disable UFO, disabled by default. 
- **UFO_CALL** (Boolean): trace function call, disabled by default.
- **UFO_TDIR** (String): the directory for your traces, by default it is ```./ufo_traces_*``` .
- **UFO_TL_BUF** (Number): buffer size for each thread, in MB. By default it is 128 (128MB).
- **UFO_NO_STACK** (Boolean): do not trace read/write on stack or thread local storage, enabled by default.
- **UFO_NO_VALUE** (Boolean): do not record read/write value, disabled by default.
- **UFO_STAT** (Boolean): print statistic data, enabled by default.

Example:
```
 $ UFO_ON=1 UFO_CALL=1 UFO_TDIR=./ufo_test_trace UFO_TL_BUF=512 ./test
```
In this example, the runtime library will create a buffer of 512MB for each thread.
When the thread local buffer is full, this buffer is pushed to a queue and the data will be flushed asynchronously to disk.

For each process, UFO will create a folder to store the thread local traces within that process, the process id is appended to the folder name. Assuming the main process is "1234", and the main process forked another process "1235",
UFO will create folder `ufo_test_trace_1234` for the main process, and `ufo_test_trace_1235` for the other process.

### 3. UFO predict bugs

To predict UaFs from a trace, first edit the configuration file `config.properties`. There are several options you may configure:

```
# your trace directory
 trace_dir=./ufo_test_trace 
# solver timeout seconds
 solver_time= 120
# default symbolizer
symbolizer=atos
# solver_mem MB
solver_mem=2000
# window size
window_size=100000
# do not solve constraints if true
fast_detect=false
```

Then run the provided executable `runufo.jar`
```
 $ java -Xmx2g -jar runufo.jar
```

#### run in Eclipse
The bug detection tool can be easily used in Eclipse. Create an Eclipse workspace and import the project `ufo-predict`. Run `UFOMain.java`. See more information in the folder `ufo-predict`.

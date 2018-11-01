#!/bin/sh
# sutomatically builds everything you need to start using UFO

# get source code for LLVM, Clang, and Compiler Runtime
svn co https://llvm.org/svn/llvm-project/llvm/tags/RELEASE_600/final/
mv final/ llvm-6.0.0/

cd llvm-6.0.0/tools
svn co https://llvm.org/svn/llvm-project/cfe/tags/RELEASE_600/final/
mv final/ clang/

cd ../projects
svn co https://llvm.org/svn/llvm-project/compiler-rt/tags/RELEASE_600/final/ 
mv final/ compiler-rt/ 
cd compiler-rt/lib && rm -r tsan/

# add UFO (UFO is built on top of thread sanitizer or tsan)
cp -r ../../../../tsan/ .

# build LLVM (this will take a couple of hours)
cd ../../../../ && mkdir build && cd build
cmake -G "Unix Makefiles" ../llvm
make -j8

# now, follow the instructions in ufo/README.md to start using UFO!
